package org.processmining.tests.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetImpl;
import org.processmining.plugins.converters.BPMNUtils;
import org.processmining.plugins.converters.PetriNetToBPMNConverter;

public class PetriNetToBPMNConverterTest {

    private static String EMPTY = BPMNUtils.EMPTY;

    private Place currentInitialPlace;

    @Before
    public void setUp()
            throws Exception {
        currentInitialPlace = null;
    }

    @Test
    public void convert_withTestCase1_expectedCorrectConversion() {
        PetrinetGraph petriNet = createPetriNetForCase1();

        BPMNDiagram diagram = new PetriNetToBPMNConverter(petriNet, currentInitialPlace)
                .convert();

        assertDiagramForCase1(diagram);
    }

    private PetrinetGraph createPetriNetForCase1() {
        // Unsound petri net with one silent activity
        PetrinetGraph result = new PetrinetImpl("label");

        Place p0 = result.addPlace("p0");
        Place p1 = result.addPlace("p1");
        Place p2 = result.addPlace("p2");
        Place p3 = result.addPlace("p3");
        Place p4 = result.addPlace("p4");
        Place p5 = result.addPlace("p5");

        currentInitialPlace = p0;

        Transition silent = result.addTransition(EMPTY);
        Transition a = result.addTransition("a");
        Transition b = result.addTransition("b");
        Transition c = result.addTransition("c");
        Transition d = result.addTransition("d");

        result.addArc(p0, silent);
        result.addArc(silent, p1);
        result.addArc(silent, p2);
        result.addArc(silent, p3);
        result.addArc(p1, a);
        result.addArc(p2, b);
        result.addArc(p3, c);
        result.addArc(a, p4);
        result.addArc(b, p4);
        result.addArc(c, p4);
        result.addArc(p4, d);
        result.addArc(d, p5);

        return result;
    }


    private void assertDiagramForCase1(BPMNDiagram diagram) {
        // check number of elements
        assertEquals(2, diagram.getEvents().size());
        assertEquals(5, diagram.getActivities().size());
        assertEquals(2, diagram.getGateways().size());
        assertEquals(10, diagram.getFlows().size());

        // check events existence
        Event start = null, end = null;
        for (Event e : diagram.getEvents()) {
            if (e.getEventType().equals(Event.EventType.START)) start = e;
            else if (e.getEventType().equals(Event.EventType.END)) end = e;
        }
        assertNotNull(start);
        assertNotNull(end);

        // check activities existence
        Activity a = null, b = null, c = null, d = null, silent = null;
        for (Activity activity : diagram.getActivities()) {
            if ("a".equals(activity.getLabel())) a = activity;
            else if ("b".equals(activity.getLabel())) b = activity;
            else if ("c".equals(activity.getLabel())) c = activity;
            else if ("d".equals(activity.getLabel())) d = activity;
            else if (EMPTY.equals(activity.getLabel())) silent = activity;
        }
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(d);
        assertNotNull(silent);

        // check gateways existence
        Gateway and = null, xor = null;
        for (Gateway g : diagram.getGateways()) {
            if (Gateway.GatewayType.DATABASED.equals(g.getGatewayType())) xor = g;
            if (Gateway.GatewayType.PARALLEL.equals(g.getGatewayType())) and = g;
        }
        assertNotNull(and);
        assertNotNull(xor);

        // check flows
        Map<BPMNNode, Set<BPMNNode>> flowMap = new HashMap<BPMNNode, Set<BPMNNode>>();
        flowMap.put(start, set(silent));
        flowMap.put(silent, set(and));
        flowMap.put(and, set(a, b, c));
        flowMap.put(a, set(xor));
        flowMap.put(b, set(xor));
        flowMap.put(c, set(xor));
        flowMap.put(xor, set(d));
        flowMap.put(d, set(end));

        assertFlowMapAppliesToDiagram(flowMap, diagram);
    }

    private Set<BPMNNode> set(BPMNNode... nodes) {
        Set<BPMNNode> result = new HashSet<BPMNNode>();
        Collections.addAll(result, nodes);
        return result;
    }

    private void assertFlowMapAppliesToDiagram(Map<BPMNNode, Set<BPMNNode>> flowMap, BPMNDiagram diagram) {
        for (Flow flow : diagram.getFlows()) {
            BPMNNode source = flow.getSource();
            BPMNNode target = flow.getTarget();
            if (!flowMap.get(source).contains(target)) fail("No flow " + source + " -> " + target + " found");
        }
    }

}
