package org.processmining.plugins.converters;

import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramImpl;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.ResetArc;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

import java.util.*;

public class PetriNetToBPMNConverter {

    private static final String EXCLUSIVE_GATEWAY = "Exclusive gateway";
    private static final String PARALLEL_GATEWAY = "Parallel gateway";

    private final PetrinetGraph petriNet;
    private BPMNDiagram bpmnDiagram;
    private Map<String, Activity> conversionMap;

    private final Place initialPlace;

    public PetriNetToBPMNConverter(PetrinetGraph petriNet, Place initialPlace) {
        if (petriNet == null) throw new IllegalArgumentException("'petriNet' is null");

        this.petriNet = petriNet;
        this.initialPlace = initialPlace;
    }

    public BPMNDiagram convert() {
        if (bpmnDiagram != null) return bpmnDiagram;

        bpmnDiagram = new BPMNDiagramImpl("BPMN diagram for " + petriNet.getLabel());
        conversionMap = new HashMap<String, Activity>();
        Set<Place> convertedPlaces = new HashSet<Place>();

        convertTransitionsToActivities(petriNet, bpmnDiagram, conversionMap);
        convertPlacesToRoutingElements(petriNet, bpmnDiagram, conversionMap, convertedPlaces);

        return bpmnDiagram;
    }

    private void convertTransitionsToActivities(PetrinetGraph petrinetGraph, BPMNDiagram bpmnDiagram,
                                                Map<String, Activity> conversionMap) {
        for (Transition transition : petrinetGraph.getTransitions()) {
            String label = BPMNUtils.EMPTY;

            if (!transition.isInvisible() && transition.getLabel() != null && !transition.getLabel().isEmpty()
                &&(!transition.getLabel().startsWith("tau"))) {
                label = transition.getLabel();
            }
            Activity activity = bpmnDiagram.addActivity(label, false, false, false, false, false);
            conversionMap.put(transition.getId().toString(), activity);
        }
    }

    private void convertPlacesToRoutingElements(PetrinetGraph petrinetGraph, BPMNDiagram bpmnDiagram,
                                                Map<String, Activity> conversionMap, Set<Place> convertedPlaces) {
        for (Place place : petrinetGraph.getPlaces()) {
            if (!convertedPlaces.contains(place)) {
                if (place == initialPlace) {
                    convertInitialPlace(place, petrinetGraph, bpmnDiagram, conversionMap, convertedPlaces);
                } else if (isFinalPlace(place, petrinetGraph)) {
                    convertFinalPlace(place, petrinetGraph, bpmnDiagram, conversionMap, convertedPlaces);
                } else {
                    convertPlace(place, petrinetGraph, bpmnDiagram, conversionMap, convertedPlaces);
                }
            }
        }
    }

    private void convertInitialPlace(Place place, PetrinetGraph petrinetGraph, BPMNDiagram bpmnDiagram,
                                     Map<String, Activity> conversionMap, Set<Place> convertedPlaces) {

        // Collect equivalent places (places with the same set of out transitions)
        Set<Place> equivalentPlaces = collectEquivalentPlaces(place, petrinetGraph);
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
        connectToOutTransitions(startEvent, place, petrinetGraph, bpmnDiagram, conversionMap, convertedPlaces);
        convertedPlaces.add(place);
    }

    private Set<Place> collectEquivalentPlaces(Place place, PetrinetGraph petrinetGraph) {
        Set<Place> equivalentPlaces = new HashSet<Place>();

        Set<Transition> outTransitions = collectOutTransitions(place, petrinetGraph);
        for (Place anyPlace : petrinetGraph.getPlaces()) {
            if (!place.equals(anyPlace)) {
                Set<Transition> outTransitionsForAnyPlace = collectOutTransitions(anyPlace, petrinetGraph);
                if (outTransitionsForAnyPlace.containsAll(outTransitions)) {
                    // We use containsAll() method due to free-choice Petri net
                    equivalentPlaces.add(anyPlace);
                }
            }
        }
        return equivalentPlaces;
    }

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

    private void connectToOutTransitions(BPMNNode startNode, Place place, PetrinetGraph petrinetGraph,
                                         BPMNDiagram bpmnDiagram, Map<String, Activity> conversionMap, Set<Place> convertedPlaces) {

        // Collect outTransitions
        Set<Transition> outTransitions = collectOutTransitions(place, petrinetGraph);

        // If amount of out transitions is greater than 1 create XOR-split
        // and appropriate sequence flow
        Gateway xorSplit = null;
        if (outTransitions.size() > 1) {
            xorSplit = bpmnDiagram.addGateway(EXCLUSIVE_GATEWAY, Gateway.GatewayType.DATABASED);
            bpmnDiagram.addFlow(startNode, xorSplit, null);
        }

        // Consider each out transition
        for (Transition outTransition : outTransitions) {
            Activity activity = conversionMap.get(outTransition.getId().toString());
            if (xorSplit == null) {
                bpmnDiagram.addFlow(startNode, activity, null);
            } else {
                bpmnDiagram.addFlow(xorSplit, activity, null);
            }
        }
    }

    private boolean isFinalPlace(Place place, PetrinetGraph petrinetGraph) {
        for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> e : petrinetGraph.getOutEdges(place)) {
            if (!(e instanceof ResetArc)) {
                return false;
            }
        }
        return true;
    }

    private void convertFinalPlace(Place place, PetrinetGraph petrinetGraph, BPMNDiagram bpmnDiagram,
                                   Map<String, Activity> conversionMap, Set<Place> convertedPlaces) {

        Event endEvent = retrieveEndEvent(bpmnDiagram);
        // Create final event
        if (endEvent == null) {
            endEvent = bpmnDiagram.addEvent("END EVENT", Event.EventType.END, null, Event.EventUse.THROW, true, null);
        }
        Set<Transition> inTransitions = collectInTransitions(place, petrinetGraph);

        for (Transition inTransition : inTransitions) {

            Activity activity = conversionMap.get(inTransition.getId().toString());
            bpmnDiagram.addFlow(activity, endEvent, null);
        }
        convertedPlaces.add(place);
    }

    private Event retrieveEndEvent(BPMNDiagram diagram) {
        Event endEvent = null;
        for (Event event : diagram.getEvents()) {
            if (event.getEventType().equals(Event.EventType.END)) {
                endEvent = event;
            }
        }

        return endEvent;
    }

    private Set<Transition> collectInTransitions(Place place, PetrinetGraph petrinetGraph) {
        Set<Transition> inTransitions = new HashSet<Transition>();
        Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = petrinetGraph
                .getInEdges(place);
        for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inEdge : inEdges) {
            inTransitions.add((Transition) inEdge.getSource());
        }
        return inTransitions;
    }

    private void convertPlace(Place place, PetrinetGraph petrinetGraph, BPMNDiagram bpmnDiagram,
                              Map<String, Activity> conversionMap, Set<Place> convertedPlaces) {

        BPMNNode startNode = convertPlacePredecessors(place, petrinetGraph, bpmnDiagram, conversionMap, convertedPlaces);
        connectToOutTransitions(startNode, place, petrinetGraph, bpmnDiagram, conversionMap, convertedPlaces);
        convertedPlaces.add(place);
    }

    private BPMNNode convertPlacePredecessors(Place place, PetrinetGraph petrinetGraph, BPMNDiagram bpmnDiagram,
                                              Map<String, Activity> conversionMap, Set<Place> convertedPlaces) {

        BPMNNode lastNode = null, andJoin = null, xorJoin = null;
        boolean hasEquivalentPlaces = false;

        Set<Place> equivalentPlaces = collectEquivalentPlaces(place, petrinetGraph);
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
            Set<Transition> inTransitions = collectInTransitions(somePlace, petrinetGraph);
            // If number of in transition is greater than 1, extra XOR-join should be created
            if (inTransitions.size() > 1) {
                xorJoin = bpmnDiagram.addGateway(EXCLUSIVE_GATEWAY, Gateway.GatewayType.DATABASED);
                for (Transition inTransition : inTransitions) {
                    lastNode = connectToInTransition(inTransition, petrinetGraph, bpmnDiagram, conversionMap, xorJoin);
                }
            } else if (inTransitions.size() == 1) {
                Transition inTransition = inTransitions.iterator().next();
                lastNode = connectToInTransition(inTransition, petrinetGraph, bpmnDiagram, conversionMap, null);
            }
            if (hasEquivalentPlaces) {
                bpmnDiagram.addFlow(lastNode, andJoin, null);
            }
        }
        return (andJoin != null ? andJoin : lastNode);
    }

    private BPMNNode connectToInTransition(Transition inTransition, PetrinetGraph petrinetGraph,
                                           BPMNDiagram bpmnDiagram, Map<String, Activity> conversionMap, BPMNNode splitNode) {

        Activity activity = conversionMap.get(inTransition.getId().toString());

        // If in transition has more than one outgoing places and-split should be used
        if (petrinetGraph.getOutEdges(inTransition).size() > 1) {
            Gateway andSplit = retrieveActivityANDSplitSuccessor(activity, bpmnDiagram);
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
        } else if (petrinetGraph.getOutEdges(inTransition).size() == 1) {
            if (splitNode != null) {
                bpmnDiagram.addFlow(activity, splitNode, null);
                return splitNode;
            } else {
                return activity;
            }
        }
        return null;
    }

    private Gateway retrieveActivityANDSplitSuccessor(Activity activity, BPMNDiagram bpmnDiagram) {
        Collection<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>> outEdges = bpmnDiagram.getOutEdges(activity);
        for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> outEdge : outEdges) {
            BPMNNode targetNode = outEdge.getTarget();
            if ((targetNode instanceof Gateway) && ((Gateway) targetNode).getGatewayType().equals(Gateway.GatewayType.PARALLEL)) {
                return (Gateway) targetNode;
            }
        }
        return null;
    }

    public Map<String, Activity> getConversionMap() {
        return conversionMap;
    }
}
