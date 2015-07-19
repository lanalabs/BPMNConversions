package org.processmining.plugins.converters;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.connections.ConnectionManager;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.DataObject;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway.GatewayType;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetImpl;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataPetriNet;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;
import org.processmining.models.semantics.petrinet.Marking;

/**
 * Conversion of a Data Petri net to the BPMN model 
 *
 * @author Anna Kalenkova
 * Dec 12, 2013
 */
@Plugin(name = "Convert Data Petri net to BPMN diagram", parameterLabels = { "Data Petri net" }, 
returnLabels = { "BPMN Diagram"}, returnTypes = { BPMNDiagram.class}, 
userAccessible = true, help = "Converts Data Petri net to BPMN diagram")
public class DataPetriNet2BPMNConverter {
	
	protected Map<String, Activity> conversionMap = null;
	protected Map<Transition, Transition> transitionsMap = new HashMap<Transition, Transition>();
	protected Map<String, DataObject> dataObjectsMap = new HashMap<String, DataObject>();
	
	@SuppressWarnings("unchecked")
	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Convert Data Petri net to BPMN", requiredParameterLabels = { 0 })
	public BPMNDiagram convert(UIPluginContext context, DataPetriNet dataPetriNet) {
		Progress progress = context.getProgress();
		progress.setCaption("Converting Data Petri net To BPMN diagram");
				
		BPMNDiagram bpmnDiagram = null;

		// Clone petri net not to make a recursion
		Object[] resultOfClone = cloneToPetrinet(context, dataPetriNet);
		PetrinetGraph clonePetrinet = (PetrinetGraph)resultOfClone[0];
		transitionsMap = (Map<Transition, Transition>)resultOfClone[1];
		
		try {
			// Convert Petri net to a BPMN diagram
			bpmnDiagram = context.tryToFindOrConstructFirstNamedObject(BPMNDiagram.class, 
					"Convert Petri net to BPMN diagram", null, null, clonePetrinet);
			// Retrieve conversion map
			conversionMap = context.tryToFindOrConstructFirstObject(Map.class, 
					BPMNConversionConnection.class, BPMNConversionConnection.CONVERSION_MAP,
					bpmnDiagram);
			
		} catch (ConnectionCannotBeObtained e) {
			context.log("Can't obtain connection for " + dataPetriNet.getLabel());
			e.printStackTrace();
		}
		
		// Add data objects to BPMN diagram
		addDataObjects(dataPetriNet, dataObjectsMap, bpmnDiagram);

		// Add data associations
		addDataAssociations(dataPetriNet, dataObjectsMap, transitionsMap, conversionMap, bpmnDiagram);
		
		// Add guards to BPMN diagram
		addGuards(dataPetriNet, dataObjectsMap, transitionsMap, conversionMap, bpmnDiagram);
		
		//Remove invisible transitions
		removeInvisibleTransitions(dataPetriNet, transitionsMap, conversionMap, bpmnDiagram);
		
		progress.setCaption("Getting BPMN Visualization");
		
		// Add connection 
		ConnectionManager connectionManager = context.getConnectionManager();
		connectionManager.addConnection(new BPMNConversionConnection("Connection between "
				+ "BPMN model" + bpmnDiagram.getLabel()
				+ ", Petri net" + clonePetrinet.getLabel()
				+ ", Data Petri net" + dataPetriNet.getLabel(),
				bpmnDiagram, clonePetrinet, dataPetriNet, conversionMap));
		
		return bpmnDiagram;
	}
	
	/**
	 * Adding data objects to BPMN diagram
	 * @param dataPetriNet
	 * @param conversionMap
	 * @param bpmnDiagram
	 */
	private void addDataObjects(DataPetriNet dataPetriNet, Map<String, DataObject> dataObjectsMap, 
			BPMNDiagram bpmnDiagram) {
		Collection<DataElement> dataElements = dataPetriNet.getVariables();
		for(DataElement dataElement : dataElements) {
			DataObject dataObject = bpmnDiagram.addDataObject(dataElement.getVarName());
			dataObjectsMap.put(dataElement.getId().toString(), dataObject);
		}
	}
	
	/**
	 * Adding data associations
	 * @param dataPetriNet
	 * @param dataObjectsMap
	 * @param conversionMap
	 * @param bpmnDiagram
	 */
	private void addDataAssociations(DataPetriNet dataPetriNet, Map<String, DataObject> dataObjectsMap, 
			Map<Transition, Transition> transitionsMap, Map<String, Activity> conversionMap,
			BPMNDiagram bpmnDiagram) {
		for(Transition transition : dataPetriNet.getTransitions()) {
			if(transition instanceof PNWDTransition) {
				PNWDTransition pnwdTransition = (PNWDTransition)transition;
				Set<DataElement> readDataElements = pnwdTransition.getReadOperations();
				for(DataElement readDataElement : readDataElements) {
					//If transition is invisible, it was deleted during the simplification
					//and we have to take visible transition successors 
					if (transition.isInvisible()) {
						for (Transition successor : transition.getVisibleSuccessors()) {
							bpmnDiagram.addDataAssociation(dataObjectsMap.get(readDataElement.getId().toString()),
									conversionMap.get(transitionsMap.get(successor).getId().toString()), "");
						}
					} else {
						bpmnDiagram.addDataAssociation(dataObjectsMap.get(readDataElement.getId().toString()),
								conversionMap.get(transitionsMap.get(transition).getId().toString()), "");
					}		
				}
				Set<DataElement> writeDataElements = pnwdTransition.getWriteOperations();
				for(DataElement writeDataElement : writeDataElements) {
					//If transition is invisible, it was deleted during the simplification
					//and we have to take visible transition successors 
					if (transition.isInvisible()) {
						for (Transition successor : transition.getVisibleSuccessors()) {
							bpmnDiagram.addDataAssociation(conversionMap.get(transitionsMap.get(successor).getId().toString()),
									dataObjectsMap.get(writeDataElement.getId().toString()), "");
						}
					} else {
						bpmnDiagram.addDataAssociation(conversionMap.get(transitionsMap.get(transition).getId().toString()),
							dataObjectsMap.get(writeDataElement.getId().toString()), "");
					}
				}
			}
		}
	}
	
	/**
	 * Add guards
	 * @param dataPetriNet
	 * @param dataObjectsMap
	 * @param transitionsMap
	 * @param conversionMap
	 * @param bpmnDiagram
	 */
	private void addGuards(DataPetriNet dataPetriNet, Map<String, DataObject> dataObjectsMap, 
			Map<Transition, Transition> transitionsMap, Map<String, Activity> conversionMap,
			BPMNDiagram bpmnDiagram) {
		for(Transition transition : dataPetriNet.getTransitions()) {
			if(transition instanceof PNWDTransition) {
				PNWDTransition pnwdTransition = (PNWDTransition) transition;
				String guard = pnwdTransition.getGuardAsString();
				if (guard != null) {
					Activity activity = conversionMap.get(transitionsMap.get(transition).getId().toString());
					Flow incomingFlow = null;
					for (BPMNEdge<?,?> edge : bpmnDiagram.getInEdges(activity)) {
						if (edge instanceof Flow) {
							incomingFlow = (Flow) edge;
							setGuardForFlow(incomingFlow, guard, bpmnDiagram);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Remove Invisible transitions
	 * @param dataPetriNet
	 * @param transitionsMap
	 * @param conversionMap
	 * @param bpmnDiagram
	 */
	private void removeInvisibleTransitions(DataPetriNet dataPetriNet, Map<Transition, Transition> transitionsMap,
			Map<String, Activity> conversionMap, BPMNDiagram bpmnDiagram) {
		for (Transition transition : dataPetriNet.getTransitions()) {
			if (transition instanceof PNWDTransition) {
				PNWDTransition pnwdTransition = (PNWDTransition) transition;
				if (pnwdTransition.isInvisible()) {
					Activity activity = conversionMap.get(transitionsMap.get(transition).getId().toString());

					// Retrieve incoming, outgoing flows and guard
					String guard = null;
					Set<BPMNEdge<?, ?>> incomingFlows = new HashSet<BPMNEdge<?, ?>>();
					Set<BPMNEdge<?, ?>> outgoingFlows = new HashSet<BPMNEdge<?, ?>>();
					for (BPMNEdge<?, ?> edge : bpmnDiagram.getInEdges(activity)) {
						if (edge instanceof Flow) {
							guard = edge.getLabel();
							incomingFlows.add(edge);
						}
					}
					for (BPMNEdge<?, ?> edge : bpmnDiagram.getOutEdges(activity)) {
						if (edge instanceof Flow) {
							outgoingFlows.add(edge);
							;
						}
					}

					// Remove activity and add new sequence flow
					if ((incomingFlows.size() == 1) && (outgoingFlows.size() == 1)) {
						BPMNNode source = incomingFlows.iterator().next().getSource();
						BPMNNode target = outgoingFlows.iterator().next().getTarget();
						bpmnDiagram.removeActivity(activity);
						bpmnDiagram.addFlow(source, target, guard);
					}
					if ((incomingFlows.size() == 1) && (outgoingFlows.size() > 1)) {
						{
							BPMNNode source = incomingFlows.iterator().next().getSource();
							Gateway parallelGateway = bpmnDiagram.addGateway("", GatewayType.PARALLEL);
							bpmnDiagram.addFlow(source, parallelGateway, guard);
							for (BPMNEdge<?, ?> outgoing : outgoingFlows) {
								BPMNNode target = outgoing.getTarget();
								bpmnDiagram.addFlow(parallelGateway, target, guard);
							}
							bpmnDiagram.removeActivity(activity);
						}
					}
				}
			}
		}

	}
	
	/**
	 * Set guard for flow
	 * @param flow
	 * @param guard
	 * @param bpmnDiagram
	 */
	private void setGuardForFlow(Flow flow, String guard, BPMNDiagram bpmnDiagram) {
		BPMNNode source = flow.getSource();
		if (source instanceof Gateway) {
			if (((Gateway) source).getGatewayType().equals(GatewayType.DATABASED)) {
				flow.setLabel(guard);
			} else if (((Gateway) source).getGatewayType().equals(GatewayType.PARALLEL)) {
				for (BPMNEdge<?,?> andIncomingFlow : bpmnDiagram.getInEdges(source)) {
					if (andIncomingFlow instanceof Flow) {
						BPMNNode andPredcessor = andIncomingFlow.getSource();
						if(andPredcessor instanceof Gateway) {
							if (((Gateway) andPredcessor).getGatewayType().equals(GatewayType.DATABASED)) {
								flow.setLabel(guard);
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Clone Petri net
	 * @param dataPetriNet
	 * @return
	 */
	private Object[] cloneToPetrinet(UIPluginContext context, DataPetriNet dataPetriNet) {
		PetrinetGraph petriNet = new PetrinetImpl(dataPetriNet.getLabel());
		Map<Transition, Transition> transitionsMap = new HashMap<Transition, Transition>();
		Map<Place, Place> placesMap = new HashMap<Place, Place>();
		
		for(Transition transition : dataPetriNet.getTransitions()) {
			transitionsMap.put(transition, petriNet.addTransition(transition.getLabel()));
		}
		for(Place place : dataPetriNet.getPlaces()) {
			placesMap.put(place, petriNet.addPlace(place.getLabel()));
		}
		for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : 
			dataPetriNet.getEdges()) {
			if ((edge.getSource() instanceof Place) && (edge.getTarget() instanceof Transition)) {
				petriNet.addArc(placesMap.get(edge.getSource()), transitionsMap.get(edge.getTarget()));
			}
			if ((edge.getSource() instanceof Transition) && (edge.getTarget() instanceof Place)) {
				petriNet.addArc(transitionsMap.get(edge.getSource()), placesMap.get(edge.getTarget()));
			}
		}
		
		Marking initialMarking = new Marking();
		try {
			InitialMarkingConnection initialMarkingConnection = context.getConnectionManager()
					.getFirstConnection(InitialMarkingConnection.class, context, dataPetriNet);
			initialMarking = (Marking)initialMarkingConnection
					.getObjectWithRole(InitialMarkingConnection.MARKING);
		} catch (ConnectionCannotBeObtained e) {
			context.log("Can't obtain connection for " + dataPetriNet.getLabel());
			e.printStackTrace();
		}
		Marking cloneMarking = new Marking();
		
		for(Place place: initialMarking.toList()) {
			cloneMarking.add(placesMap.get(place));
		}
	
		context.getConnectionManager().addConnection(new InitialMarkingConnection(petriNet, cloneMarking));
		
		return new Object[] {petriNet, transitionsMap, cloneMarking};
	}
}
