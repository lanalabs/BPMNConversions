package org.processmining.plugins.converters.bpmn2pn;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.DataAssociation;
import org.processmining.models.graphbased.directed.bpmn.elements.DataObject;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataPetriNet;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PetriNetWithData;

/**
 * Conversion of BPMN to Petri net with data
 * 
 * 
 * @author Anna Kalenkova July 27, 2014
 */
public class BPMN2DataPetriNetConverter extends BPMN2PetriNetConverter {
	
	// Map from activities to transitions
	private Map<Activity, Transition> activitiesMap = new HashMap<Activity, Transition>();
	
	// Data objects map
	private Map<DataObject, DataElement> dataObjectMap = new HashMap<DataObject, DataElement>();
	
	// Data Petri net
	private DataPetriNet dataPetriNet;
	
	public BPMN2DataPetriNetConverter(BPMNDiagram bpmn) {
		super(bpmn);
	}

	public boolean convertWithData() {
	   
		// Call control-flow conversion
		super.convert();

		// Clone Petri net to Data Petri net
		clonePetriNetToDataPetriNet();
		
		// Construct activities map
		constructActivitiesMap();
		
		// Convert data objects
		convertDataObjects();
		
		// Convert associations
		convertAssociations();
		
		// Convert guards
		convertGuards();
			
		return errors.size() == 0;
	}
	
	public DataPetriNet getDataPetriNet() {
		return dataPetriNet;
	}
	
	/**
	 * 
	 * Construct a map from activities to transitions
	 */
	private void constructActivitiesMap() {
		for(Activity activity : bpmn.getActivities()) {
			for(Transition transition : dataPetriNet.getTransitions()) {
				if ((transition.getLabel() != null) 
					&& (transition.getLabel().contains(activity.getLabel()))) {
					activitiesMap.put(activity, transition);
				}
			}
		}
	}
	
	/**
	 * 
	 * Convert data objects
	 */
	private void convertDataObjects() {
		for(DataObject dataObject : bpmn.getDataObjects()) {
			DataElement dataElement 
				= dataPetriNet.addVariable(dataObject.getLabel(), java.lang.String.class, null, null);
			dataObjectMap.put(dataObject, dataElement);
		}
	}
	
	/**
	 * 
	 * Convert associations
	 */
	private void convertAssociations() {
		for (DataAssociation association : bpmn.getDataAssociations()) {
			BPMNNode source = association.getSource();
			BPMNNode target = association.getTarget();
			if ((source instanceof DataObject) && (target instanceof Activity)) {
				dataPetriNet.assignReadOperation(activitiesMap.get(target), dataObjectMap.get(source));
			}
			if ((source instanceof Activity) && (target instanceof DataObject)) {
				dataPetriNet.assignWriteOperation(activitiesMap.get(source), dataObjectMap.get(target));
			}
		}
	}
	
	/**
	 * 
	 * Convert guards
	 */
	private void convertGuards() {
		for (Flow sequenceFlow : bpmn.getFlows()) {
			String guard = sequenceFlow.getLabel();
			if ((guard != null) && (guard != "")) {
				if (sequenceFlow.getTarget() instanceof Activity) {
					Activity activity = (Activity) (sequenceFlow.getTarget());
					Transition transition = activitiesMap.get(activity);
					try {
						dataPetriNet.setGuard(transition, guard);
					} catch (ParseException e) {
						e.printStackTrace();
						errors.add("Parse guard exception " + guard);
					}
				}
			}
		}
	}
	
	/**
	 * 
	 * Clone Petri net to data Petri net
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void clonePetriNetToDataPetriNet() {
		
		// Map for plain and data Petri nets places		
		Map<Place, Place> placesMap = new HashMap();
		
		// Map for plain and data Petri nets transitions
		Map<Transition, Transition> transitionsMap = new HashMap();
		
		dataPetriNet = new PetriNetWithData(net.getLabel());
		for(Place place : net.getPlaces()) {
			Place newPlace = dataPetriNet.addPlace(place.getLabel());
			placesMap.put(place, newPlace);
		}
		for(Transition transition : net.getTransitions()) {
			Transition newTransition = dataPetriNet.addTransition(transition.getLabel());
			transitionsMap.put(transition, newTransition);
		}
		for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : net.getEdges()) {
			if ((edge.getSource() instanceof Transition) && (edge.getTarget() instanceof Place)) {
				dataPetriNet.addArc(transitionsMap.get(edge.getSource()), placesMap.get(edge.getTarget()));
			}
			if ((edge.getSource() instanceof Place) && (edge.getTarget() instanceof Transition)) {
				dataPetriNet.addArc(placesMap.get(edge.getSource()), transitionsMap.get(edge.getTarget()));
			}
		}
	}
}
