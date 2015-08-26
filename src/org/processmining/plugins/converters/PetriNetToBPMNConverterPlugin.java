package org.processmining.plugins.converters;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.connections.ConnectionManager;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.connections.petrinets.structural.FreeChoiceInfoConnection;
import org.processmining.models.graphbased.directed.DirectedGraphNode;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventTrigger;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventType;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventUse;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.ResetInhibitorNet;
import org.processmining.models.graphbased.directed.petrinet.ResetNet;
import org.processmining.models.graphbased.directed.petrinet.analysis.NetAnalysisInformation;
import org.processmining.models.graphbased.directed.petrinet.analysis.NetAnalysisInformation.UnDetBool;
import org.processmining.models.graphbased.directed.petrinet.elements.Arc;
import org.processmining.models.graphbased.directed.petrinet.elements.InhibitorArc;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.ResetArc;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.ResetInhibitorNetImpl;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.graphalgorithms.DFS;

/**
 * Conversion of a Petri net to BPMN model
 * 
 * @author Anna Kalenkova 
 * Jul 18, 2013
 */
@Plugin(name = "Convert Petri net to BPMN diagram", parameterLabels = { "Petri net" }, returnLabels = {
		"BPMN Diagram, ", "Conversion map" }, returnTypes = { BPMNDiagram.class, Map.class }, userAccessible = true, help = "Converts Petri net to BPMN diagram")
public class PetriNetToBPMNConverterPlugin {

    private Place initialPlace;
	private Transition initialTransition;

	@SuppressWarnings("unchecked")
	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Convert Petri net to BPMN", requiredParameterLabels = { 0 })
	public Object[] convert(UIPluginContext context, PetrinetGraph petrinetGraph) {

		Progress progress = context.getProgress();
		progress.setCaption("Converting Petri net To BPMN diagram");

        Marking initialMarking = retrieveMarking(context, petrinetGraph);

		// Clone to Petri net with marking
		Object[] cloneResult = cloneToPetrinet(petrinetGraph, initialMarking);
		PetrinetGraph clonePetrinet = (PetrinetGraph) cloneResult[0];
		Map<Transition, Transition> transitionsMap = (Map<Transition, Transition>) cloneResult[1];
		Marking cloneMarking = (Marking) cloneResult[2];

		// Check whether Petri net without reset arcs is a free-choice net
		Map<PetrinetNode, Set<PetrinetNode>> deletedResetArcs = deleteResetArcs(clonePetrinet);
		boolean isFreeChoice = petriNetIsFreeChoice(context, clonePetrinet);
		restoreResetArcs(deletedResetArcs, clonePetrinet);

		// If Petri net is not a free-choice net it will be transformed
		if (!isFreeChoice) {
			String nonFreeChoiceMessage = "Initial Petri net is not a free-choice net and "
					+ "will be converted to resembling free-choice Petri net";
			context.log(nonFreeChoiceMessage);
			JPanel warningPanel = new JPanel();
			warningPanel.add(new JLabel(nonFreeChoiceMessage));
			context.showWizard("Petri net to BPMN conversion", true, true, warningPanel);
			convertToResemblingFreeChoice(clonePetrinet);
		}

		// Convert to a Petri net with one source place if needed
		convertToPetrinetWithOneSourcePlace(clonePetrinet, cloneMarking);

		// Handle transitions without incoming sequence flows
		handleTransitionsWithoutIncomingFlows(clonePetrinet);
		
		// Remove places without incoming sequence flows
		removeDeadPlaces(clonePetrinet);

		// Convert Petri net to a BPMN diagram
        PetriNetToBPMNConverter converter = new PetriNetToBPMNConverter(clonePetrinet, initialPlace);
        BPMNDiagram bpmnDiagram = converter.convert();
		Map<String, Activity> conversionMap = converter.getConversionMap();

		// Simplify BPMN diagram
		BPMNUtils.simplifyBPMNDiagram(conversionMap, bpmnDiagram);
		
		// Handle activities without outgoing sequence flows
		handleActivitiesWithoutOutgoingFlows(bpmnDiagram);

		//Add end event
		//addEndEvent(bpmnDiagram);

		// Rebuild conversion map to restore connections with the initial Petri net 
		conversionMap = rebuildConversionMap(conversionMap, transitionsMap);

		progress.setCaption("Getting BPMN Visualization");

		// Add connection 
		ConnectionManager connectionManager = context.getConnectionManager();
		connectionManager.addConnection(new BPMNConversionConnection("Connection between " + "BPMN model"
				+ bpmnDiagram.getLabel() + ", Petri net" + petrinetGraph.getLabel(), bpmnDiagram, petrinetGraph,
				conversionMap));
		
		return new Object[] { bpmnDiagram, conversionMap };
	}

	/**
	 * Converting an arbitrary Petri net to a Petri net with a single source
	 * place
	 * 
	 * @param petriNet
	 * @param marking
	 */
	private void convertToPetrinetWithOneSourcePlace(PetrinetGraph petriNet, Marking marking) {
		initialPlace = petriNet.addPlace("");
		initialTransition = petriNet.addTransition("");
		initialTransition.setInvisible(true);
		petriNet.addArc(initialPlace, initialTransition);
		for (Place place : marking.toList()) {
			petriNet.addArc(initialTransition, place);
		}
	}

	
	/**
	 * Handle transitions without incoming sequence flows
	 * 
	 * @param petriNet
	 */
	@SuppressWarnings("rawtypes")
	private void handleTransitionsWithoutIncomingFlows(PetrinetGraph petriNet) {
		
		// Handle transitions without incoming edges
		for (Transition transition : petriNet.getTransitions()) {
			if ((petriNet.getInEdges(transition) == null) || (petriNet.getInEdges(transition).size() == 0)) {
				Place newPlace = petriNet.addPlace("");
				petriNet.addArc(initialTransition, newPlace);
				petriNet.addArc(newPlace, transition);
				petriNet.addArc(transition, newPlace);
			}
		}
	}
	
	private void handleActivitiesWithoutOutgoingFlows(BPMNDiagram bpmnDiagram) {
		// Handle activities without paths to the end event		
		Event startEvent = retrieveStartEvent(bpmnDiagram);
		Event endEvent = retrieveEndEvent(bpmnDiagram);
		DFS dfs = new DFS(bpmnDiagram, startEvent);

		Set<Activity> acivitiesWithoutPathToEndEvent = findActivitiesWithoutPathToEndEvent(bpmnDiagram, dfs);
		Set<Activity> currentActivities = new HashSet<Activity>();
		currentActivities.addAll(acivitiesWithoutPathToEndEvent);

		for (Activity activity1 : acivitiesWithoutPathToEndEvent) {
			for (Activity activity2 : acivitiesWithoutPathToEndEvent) {
				if (dfs.findDescendants(activity2).contains(activity1) && !activity1.equals(activity2)) {
					if (currentActivities.contains(activity1)) {
						currentActivities.remove(activity2);
					}
				}
			}
		}

		for (Activity activity : currentActivities) {
			bpmnDiagram.addFlow(activity, endEvent, "");
		}
	}
	
	/**
	 * Find activities without a path to end event
	 * 
	 * @param bpmnDiagram
	 * @return
	 */
	private Set<Activity> findActivitiesWithoutPathToEndEvent(BPMNDiagram bpmnDiagram, DFS dfs) {
			
			Set<Activity> resultSet = new HashSet<Activity>();
			Event endEvent = retrieveEndEvent(bpmnDiagram);
			// Find activities without paths to end event
			for (Activity activity : bpmnDiagram.getActivities()) {
				Set<DirectedGraphNode> descendants = dfs.findDescendants(activity);
								
				boolean hasPathToEndEvent = false; 
				for(DirectedGraphNode descendant : descendants) {
					if (descendant.equals(endEvent)) {
						hasPathToEndEvent = true;
					}
				}
				if(!hasPathToEndEvent) {
					resultSet.add(activity);
				}
			}
			return resultSet;
		}
	

	/**
	 * Remove places without incoming sequence flows and corresponding output
	 * transitions
	 * 
	 * @param petriNet
	 */
	private void removeDeadPlaces(PetrinetGraph petriNet) {
		boolean hasDeadPlaces;
		Set<Place> toRemove = new HashSet<Place>();
		do {
			hasDeadPlaces = false;
			for (Place place : petriNet.getPlaces()) {			
				if (place != initialPlace) {
					if ((petriNet.getInEdges(place) == null) || (petriNet.getInEdges(place).size() == 0)) {
						Collection<Transition> outTransitions = collectOutTransitions(place, petriNet);
						for (Transition transition : outTransitions) {
							petriNet.removeTransition(transition);
						}
						toRemove.add(place);
						hasDeadPlaces = true;
					}
				}
			}
			for(Place place : toRemove) {
				petriNet.removePlace(place);
			}
		} while (hasDeadPlaces);
	}

    /**
	 * isInitialPlace Retrieve marking for a Petri net graph
	 * 
	 * @param context
	 * @param petrinetGraph
	 * @return
	 */
	private Marking retrieveMarking(UIPluginContext context, PetrinetGraph petrinetGraph) {
		Marking marking = new Marking();
		try {
			InitialMarkingConnection initialMarkingConnection = context.getConnectionManager().getFirstConnection(
					InitialMarkingConnection.class, context, petrinetGraph);
			marking = initialMarkingConnection.getObjectWithRole(InitialMarkingConnection.MARKING);
			if ((marking != null) && (marking.size() == 0)) {
				Place sourcePlace = retrieveSourcePlace(petrinetGraph);
				marking.add(sourcePlace);
			}
		} catch (ConnectionCannotBeObtained e) {
			Place sourcePlace = retrieveSourcePlace(petrinetGraph);
			marking.add(sourcePlace);
		}
		return marking;
	}
	
	private Place retrieveSourcePlace(PetrinetGraph petrinetGraph) {
		for(Place place : petrinetGraph.getPlaces()) {
			if ((petrinetGraph.getInEdges(place) == null) || (petrinetGraph.getInEdges(place).size() == 0)) {
				return place;
			}
		}
		return null;
	}

	/**
	 * Rebuilding conversion map to restore connections with the initial Petri
	 * net
	 * 
	 * @param conversionMap
	 * @param transitionsMap
	 * @return
	 */
	private Map<String, Activity> rebuildConversionMap(Map<String, Activity> conversionMap,
			Map<Transition, Transition> transitionsMap) {
		Map<String, Activity> newConversionMap = new HashMap<String, Activity>();
		for (Transition transition : transitionsMap.keySet()) {
			newConversionMap.put(transition.getId().toString(),
					conversionMap.get(transitionsMap.get(transition).getId().toString()));
		}
		return newConversionMap;
	}

	/**
	 * Convert to resembling free-choice net
	 * 
	 * @param petrinetGraph
	 */
	private void convertToResemblingFreeChoice(PetrinetGraph petrinetGraph) {
		// Set of common places which should be splited
		Set<Place> nonFreePlaces = new HashSet<Place>();
		//For each pair of transitions
		for (Transition t1 : petrinetGraph.getTransitions()) {
			for (Transition t2 : petrinetGraph.getTransitions()) {
				Set<Place> inPlaces1 = collectInPlaces(t1, petrinetGraph);
				Set<Place> inPlaces2 = collectInPlaces(t2, petrinetGraph);
				Set<Place> commonPlaces = new HashSet<Place>();
				boolean hasCommonPlace = false;
				boolean hasDiffPlaces = false;
				for (Place p1 : inPlaces1) {
					for (Place p2 : inPlaces2) {
						if (p1.equals(p2)) {
							hasCommonPlace = true;
							commonPlaces.add(p1);
						} else {
							hasDiffPlaces = true;
						}
					}
				}
				if (hasCommonPlace && hasDiffPlaces) {
					nonFreePlaces.addAll(commonPlaces);
				}
			}
		}
		splitNonFreePlaces(petrinetGraph, nonFreePlaces);
	}

	/**
	 * Split non-free places
	 * 
	 * @param petrinetGraph
	 * @param nonFreePlaces
	 */
	private void splitNonFreePlaces(PetrinetGraph petrinetGraph, Set<Place> nonFreePlaces) {
		for (Place place : nonFreePlaces) {
			for (PetrinetEdge<?, ?> outArc : petrinetGraph.getOutEdges(place)) {
				Transition outTransition = (Transition) outArc.getTarget();
				petrinetGraph.removeEdge(outArc);
				Place newPlace = petrinetGraph.addPlace("");
				Transition newTransition = petrinetGraph.addTransition("");
				newTransition.setInvisible(true);
				petrinetGraph.addArc(newPlace, outTransition);
				petrinetGraph.addArc(newTransition, newPlace);
				petrinetGraph.addArc(place, newTransition);
			}
		}
	}

	/**
	 * Check whether Petri net is a Free-Choice
	 * 
	 * @param context
	 * @param petrinetGraph
	 * @return
	 */
	private boolean petriNetIsFreeChoice(PluginContext context, PetrinetGraph petrinetGraph) {
		NetAnalysisInformation.FREECHOICE fCRes = null;
		try {
			fCRes = context.tryToFindOrConstructFirstObject(NetAnalysisInformation.FREECHOICE.class,
					FreeChoiceInfoConnection.class, "Free Choice information of " + petrinetGraph.getLabel(),
					petrinetGraph);
		} catch (ConnectionCannotBeObtained e) {
			context.log("Can't obtain connection for " + petrinetGraph.getLabel());
			e.printStackTrace();
		}
		return fCRes.getValue().equals(UnDetBool.TRUE);
	}

	/**
	 * Delete reset arcs
	 * 
	 * @param petrinetGraph
	 * @return
	 */
	private Map<PetrinetNode, Set<PetrinetNode>> deleteResetArcs(PetrinetGraph petrinetGraph) {

		Map<PetrinetNode, Set<PetrinetNode>> deletedEdges = new HashMap<PetrinetNode, Set<PetrinetNode>>();
		for (Place place : petrinetGraph.getPlaces()) {
			Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = petrinetGraph
					.getOutEdges(place);
			for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : outEdges) {
				if (edge instanceof ResetArc) {
					petrinetGraph.removeEdge(edge);
					Set<PetrinetNode> targetNodes;
					if (deletedEdges.get(edge) == null) {
						targetNodes = new HashSet<PetrinetNode>();
						deletedEdges.put(place, targetNodes);
					} else {
						targetNodes = deletedEdges.get(edge);
					}
					targetNodes.add(edge.getTarget());
					deletedEdges.put(place, targetNodes);
				}
			}
		}

		return deletedEdges;
	}

	/**
	 * Restore reset arcs
	 * 
	 * @param petrinetGraph
	 * @return
	 */
	private void restoreResetArcs(Map<PetrinetNode, Set<PetrinetNode>> resetArcs, PetrinetGraph petrinetGraph) {

		for (PetrinetNode place : resetArcs.keySet()) {
			Set<PetrinetNode> transitions = resetArcs.get(place);
			for (PetrinetNode transition : transitions) {
				if (petrinetGraph instanceof ResetInhibitorNet) {
					((ResetInhibitorNet) petrinetGraph).addResetArc((Place) place, (Transition) transition);
				}
				if (petrinetGraph instanceof ResetNet) {
					System.out.println("Reset arc is added");
					((ResetNet) petrinetGraph).addResetArc((Place) place, (Transition) transition);
				}
			}
		}
	}

    /**
	 * Retrieve end event for the BPMN Diagram
	 * 
	 * @param diagram
	 * @return
	 */
	private Event retrieveEndEvent(BPMNDiagram diagram) {
		Event endEvent = null;
		for (Event event : diagram.getEvents()) {
			if (event.getEventType().equals(EventType.END)) {
				endEvent = event;
			}
		}

		if(endEvent == null) {
			endEvent = diagram.addEvent("", EventType.END, EventTrigger.NONE, EventUse.THROW, true, null);
		}
		return endEvent;
	}
	
	/**
	 * Retrieve start event for the BPMN Diagram
	 * 
	 * @param diagram
	 * @return
	 */
	private Event retrieveStartEvent(BPMNDiagram diagram) {
		Event startEvent = null;
		for (Event event : diagram.getEvents()) {
			if (event.getEventType().equals(EventType.START)) {
				startEvent = event;
			}
		}

		return startEvent;
	}


    /**
	 * Collect out transitions for a place in the Petri net
	 * 
	 * @param place
	 * @param petrinetGraph
	 * @return
	 */
	private Set<Transition> collectOutTransitions(Place place, PetrinetGraph petrinetGraph) {
		Set<Transition> outTransitions = new HashSet<Transition>();
		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = petrinetGraph
				.getOutEdges(place);
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> outEdge : outEdges) {
			if (!(outEdge instanceof ResetArc)) {
				outTransitions.add((Transition) outEdge.getTarget());
			}
		}
		return outTransitions;
	}

	/**
	 * Collect in places for a transition in the Petri net
	 * 
	 * @param transition
	 * @param petrinetGraph
	 * @return
	 */
	private Set<Place> collectInPlaces(Transition transition, PetrinetGraph petrinetGraph) {
		Set<Place> inPlaces = new HashSet<Place>();
		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = petrinetGraph
				.getInEdges(transition);
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inEdge : inEdges) {
			inPlaces.add((Place) inEdge.getSource());
		}
		return inPlaces;
	}


    /**
	 * Clone Petri net
	 * 
	 * @param dataPetriNet
	 * @return
	 */
	private Object[] cloneToPetrinet(PetrinetGraph petriNet, Marking marking) {
		ResetInhibitorNet clonePetriNet = new ResetInhibitorNetImpl(petriNet.getLabel());
		Map<Transition, Transition> transitionsMap = new HashMap<Transition, Transition>();
		Map<Place, Place> placesMap = new HashMap<Place, Place>();
		Marking newMarking = new Marking();

		for (Transition transition : petriNet.getTransitions()) {
			Transition newTransition = clonePetriNet.addTransition(transition.getLabel());
			newTransition.setInvisible(transition.isInvisible());
			transitionsMap.put(transition, newTransition);
		}
		for (Place place : petriNet.getPlaces()) {
			placesMap.put(place, clonePetriNet.addPlace(place.getLabel()));
		}
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : petriNet.getEdges()) {
			if (edge instanceof InhibitorArc) {
				if ((edge.getSource() instanceof Place) && (edge.getTarget() instanceof Transition)) {
					clonePetriNet
							.addInhibitorArc(placesMap.get(edge.getSource()), transitionsMap.get(edge.getTarget()));
				}
			}
			if (edge instanceof ResetArc) {
				if ((edge.getSource() instanceof Place) && (edge.getTarget() instanceof Transition)) {
					clonePetriNet.addResetArc(placesMap.get(edge.getSource()), transitionsMap.get(edge.getTarget()));
				}
			}
			if (edge instanceof Arc) {
				if ((edge.getSource() instanceof Place) && (edge.getTarget() instanceof Transition)) {
					clonePetriNet.addArc(placesMap.get(edge.getSource()), transitionsMap.get(edge.getTarget()));
				}
				if ((edge.getSource() instanceof Transition) && (edge.getTarget() instanceof Place)) {
					clonePetriNet.addArc(transitionsMap.get(edge.getSource()), placesMap.get(edge.getTarget()));
				}
			}
		}

		// Construct marking for the clone Petri net
		if (marking != null) {
			for (Place markedPlace : marking.toList()) {
				newMarking.add(placesMap.get(markedPlace));
			}
		}

		return new Object[] { clonePetriNet, transitionsMap, newMarking };
	}
}
