package org.processmining.plugins.converters;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramImpl;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventTrigger;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventType;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventUse;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway.GatewayType;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.ResetArc;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;

public class PetriNetToBPMNConverter {

    private static final String EXCLUSIVE_GATEWAY = "Exclusive gateway";
    private static final String PARALLEL_GATEWAY = "Parallel gateway";

    private final PetrinetGraph petriNet;
    private BPMNDiagram bpmnDiagram;
    private Map<String, Activity> transitionConversionMap;
    private Map<Place, Flow> placeConversionMap;

    private final Place initialPlace;
    private final Marking finalMarking;

    public PetriNetToBPMNConverter(PetrinetGraph petriNet, Place initialPlace, Marking finalMarking) {
        if (petriNet == null) throw new IllegalArgumentException("'petriNet' is null");

        this.petriNet = petriNet;
        this.initialPlace = initialPlace;
        this.finalMarking = finalMarking;
    }

    public BPMNDiagram convert() {
        if (bpmnDiagram != null) return bpmnDiagram;

        bpmnDiagram = new BPMNDiagramImpl("BPMN diagram for " + petriNet.getLabel());
        transitionConversionMap = new HashMap<String, Activity>();
        placeConversionMap = new HashMap<Place, Flow>();
        Set<Place> convertedPlaces = new HashSet<Place>();

        convertTransitionsToActivities();
        convertPlacesToRoutingElements(convertedPlaces);
        addFinalMarking();

        return bpmnDiagram;
    }
    
    private void addFinalMarking() {
    	if ((finalMarking != null) && (finalMarking.size() > 0)) {
    		for(Place place : finalMarking) {
    			Flow flow = placeConversionMap.get(place);
    			if(flow != null) {
    				BPMNNode target = flow.getTarget();
    				BPMNNode source = flow.getSource();
    				if(target instanceof Event) {
    					if(((Event)target).getEventType().equals(EventType.END)) {
    						continue;
    					}
    				}
    				bpmnDiagram.removeEdge(flow);
    				Gateway xor = bpmnDiagram.addGateway("", GatewayType.DATABASED);
    				bpmnDiagram.addFlow(source, xor, "");
    				bpmnDiagram.addFlow(xor, target, "");
    				Event endEvent = bpmnDiagram.addEvent("", EventType.END, EventTrigger.NONE, EventUse.THROW, true, null);
    				bpmnDiagram.addFlow(xor, endEvent, "");
    			}
    		}
    	}
    }

    private void convertTransitionsToActivities() {
        for (Transition transition : petriNet.getTransitions()) {
            String label = BPMNUtils.EMPTY;

            if (!transition.isInvisible() && transition.getLabel() != null && !transition.getLabel().isEmpty()
                &&(!transition.getLabel().startsWith("tau"))) {
                label = transition.getLabel();
            }
            Activity activity = bpmnDiagram.addActivity(label, false, false, false, false, false);
            transitionConversionMap.put(transition.getId().toString(), activity);
        }
    }

    private void convertPlacesToRoutingElements(Set<Place> convertedPlaces) {
        for (Place place : petriNet.getPlaces()) {
            if (!convertedPlaces.contains(place)) {
                if (place == initialPlace) {
                    convertInitialPlace(place, convertedPlaces);
                } else if (isFinalPlace(place)) {
                    convertFinalPlace(place, convertedPlaces);
                } else {
                    convertPlace(place, convertedPlaces);
                }
            }
        }
    }

    private void convertInitialPlace(Place place, Set<Place> convertedPlaces) {

        // Collect equivalent places (places with the same set of out transitions)
        Set<Place> equivalentPlaces = collectEquivalentPlaces(place);
        for (Place equivalentPlace : equivalentPlaces) {
            if (equivalentPlace == initialPlace) {
                // If the equivalent place is initial itself it should not be considered at all
                convertedPlaces.add(equivalentPlace);
            } else {
                // If there is equivalent place which is not initial this place should not be
                // considered
                convertedPlaces.add(place);
                return;
            }
        }
        Event startEvent = bpmnDiagram.addEvent("START EVENT", Event.EventType.START, null, Event.EventUse.CATCH, true, null);
        connectToOutTransitions(startEvent, place, convertedPlaces);
        convertedPlaces.add(place);
    }

    private Set<Place> collectEquivalentPlaces(Place place) {
        Set<Place> equivalentPlaces = new HashSet<Place>();

        Set<Transition> outTransitions = collectOutTransitions(place);
        for (Place anyPlace : petriNet.getPlaces()) {
            if (!place.equals(anyPlace)) {
                Set<Transition> outTransitionsForAnyPlace = collectOutTransitions(anyPlace);
                if (outTransitionsForAnyPlace.containsAll(outTransitions)) {
                    // We use containsAll() method due to free-choice Petri net
                    equivalentPlaces.add(anyPlace);
                }
            }
        }
        return equivalentPlaces;
    }

    private Set<Transition> collectOutTransitions(Place place) {
        Set<Transition> outTransitions = new HashSet<Transition>();
        Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = petriNet
                .getOutEdges(place);
        for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> outEdge : outEdges) {
            if (!(outEdge instanceof ResetArc)) {
                outTransitions.add((Transition) outEdge.getTarget());
            }
        }
        return outTransitions;
    }

    private void connectToOutTransitions(BPMNNode startNode, Place place, Set<Place> convertedPlaces) {

        // Collect outTransitions
        Set<Transition> outTransitions = collectOutTransitions(place);

        // If amount of out transitions is greater than 1 create XOR-split
        // and appropriate sequence flow
        Gateway xorSplit = null;
        if (outTransitions.size() > 1) {
            xorSplit = bpmnDiagram.addGateway(EXCLUSIVE_GATEWAY, Gateway.GatewayType.DATABASED);
            Flow flow = bpmnDiagram.addFlow(startNode, xorSplit, null);
            placeConversionMap.put(place, flow);
        }

        // Consider each out transition
        for (Transition outTransition : outTransitions) {
            Activity activity = transitionConversionMap.get(outTransition.getId().toString());
            if (xorSplit == null) {
            	Flow flow = bpmnDiagram.addFlow(startNode, activity, null);
            	placeConversionMap.put(place, flow);
            } else {
                bpmnDiagram.addFlow(xorSplit, activity, null);
            }
        }
    }

    private boolean isFinalPlace(Place place) {
        for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> e : petriNet.getOutEdges(place)) {
            if (!(e instanceof ResetArc)) {
                return false;
            }
        }
        return true;
    }

    private void convertFinalPlace(Place place, Set<Place> convertedPlaces) {

        Event endEvent = retrieveEndEvent();
        // Create final event
        if (endEvent == null) {
            endEvent = bpmnDiagram.addEvent("END EVENT", Event.EventType.END, null, Event.EventUse.THROW, true, null);
        }
        Set<Transition> inTransitions = collectInTransitions(place);

        for (Transition inTransition : inTransitions) {

            Activity activity = transitionConversionMap.get(inTransition.getId().toString());
            bpmnDiagram.addFlow(activity, endEvent, null);
        }
        convertedPlaces.add(place);
    }

    private Event retrieveEndEvent() {
        Event endEvent = null;
        for (Event event : bpmnDiagram.getEvents()) {
            if (event.getEventType().equals(Event.EventType.END)) {
                endEvent = event;
            }
        }

        return endEvent;
    }

    private Set<Transition> collectInTransitions(Place place) {
        Set<Transition> inTransitions = new HashSet<Transition>();
        Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = petriNet
                .getInEdges(place);
        for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inEdge : inEdges) {
            inTransitions.add((Transition) inEdge.getSource());
        }
        return inTransitions;
    }

    private void convertPlace(Place place, Set<Place> convertedPlaces) {

        BPMNNode startNode = convertPlacePredecessors(place, convertedPlaces);
        connectToOutTransitions(startNode, place, convertedPlaces);
        convertedPlaces.add(place);
    }

    private BPMNNode convertPlacePredecessors(Place place, Set<Place> convertedPlaces) {

        BPMNNode lastNode = null, andJoin = null, xorJoin = null;
        boolean hasEquivalentPlaces = false;

        Set<Place> equivalentPlaces = collectEquivalentPlaces(place);
        hasEquivalentPlaces = (equivalentPlaces.size() > 0) ? true : false;

        Set<Place> allPlaces = new HashSet<Place>();
        allPlaces.addAll(equivalentPlaces);
        allPlaces.add(place);

        for (Place somePlace : allPlaces) {
            if (somePlace == initialPlace) {
                // If the equivalent place is initial it should not be considered at all
                convertedPlaces.add(somePlace);
                continue;
            }
            // If exists equivalent place, AND-join should be created
            if (hasEquivalentPlaces) {
                if (andJoin == null) {
                    andJoin = bpmnDiagram.addGateway(PARALLEL_GATEWAY, Gateway.GatewayType.PARALLEL);
                }
                if (!place.equals(somePlace)) {
                    convertedPlaces.add(somePlace);
                }
            }
            Set<Transition> inTransitions = collectInTransitions(somePlace);
            // If number of in transition is greater than 1, extra XOR-join should be created
            if (inTransitions.size() > 1) {
                xorJoin = bpmnDiagram.addGateway(EXCLUSIVE_GATEWAY, Gateway.GatewayType.DATABASED);
                for (Transition inTransition : inTransitions) {
                    lastNode = connectToInTransition(inTransition, xorJoin);
                }
            } else if (inTransitions.size() == 1) {
                Transition inTransition = inTransitions.iterator().next();
                lastNode = connectToInTransition(inTransition, null);
            }
            if (hasEquivalentPlaces) {
                Flow flow = bpmnDiagram.addFlow(lastNode, andJoin, null);
                placeConversionMap.put(somePlace, flow);
            }
        }
        return (andJoin != null ? andJoin : lastNode);
    }

    private BPMNNode connectToInTransition(Transition inTransition, BPMNNode splitNode) {

        Activity activity = transitionConversionMap.get(inTransition.getId().toString());

        // If in transition has more than one outgoing places and-split should be used
        if (petriNet.getOutEdges(inTransition).size() > 1) {
            Gateway andSplit = retrieveActivityANDSplitSuccessor(activity);
            if (andSplit == null) {
                andSplit = bpmnDiagram.addGateway(PARALLEL_GATEWAY, Gateway.GatewayType.PARALLEL);
                bpmnDiagram.addFlow(activity, andSplit, null);
            }
            if (splitNode != null) {
                bpmnDiagram.addFlow(andSplit, splitNode, null);
                return splitNode;
            } else {
                return andSplit;
            }

            // If in transition has one outgoing place
        } else if (petriNet.getOutEdges(inTransition).size() == 1) {
            if (splitNode != null) {
                bpmnDiagram.addFlow(activity, splitNode, null);
                return splitNode;
            } else {
                return activity;
            }
        }
        return null;
    }

    private Gateway retrieveActivityANDSplitSuccessor(Activity activity) {
        Collection<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>> outEdges = bpmnDiagram.getOutEdges(activity);
        for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> outEdge : outEdges) {
            BPMNNode targetNode = outEdge.getTarget();
            if ((targetNode instanceof Gateway) && ((Gateway) targetNode).getGatewayType().equals(Gateway.GatewayType.PARALLEL)) {
                return (Gateway) targetNode;
            }
        }
        return null;
    }

    public Map<String, Activity> getTransitionConversionMap() {
        return transitionConversionMap;
    }
    
    public Map<Place, Flow> getPlaceConversionMap() {
        return placeConversionMap;
    }
}
