package org.processmining.tests.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.processmining.plugins.converters.BPMNUtils.simplifyBPMNDiagram;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramImpl;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.plugins.converters.BPMNUtils;

public class BPMNUtilsTest {

    private static final boolean DEFAULT_LOOPED = false;
    private static final boolean DEFAULT_AD_HOC = false;
    private static final boolean DEFAULT_COMPENSATION = false;
    private static final boolean DEFAULT_MULTI_INSTANCE = false;
    private static final boolean DEFAULT_COLLAPSED = false;
    private static final String EMPTY_LABEL = BPMNUtils.EMPTY;

    private Map<String, Activity> currentConversionMap;
    private Map<BPMNNode, Set<BPMNNode>> currentFlowMap;

    @Before
    public void setUp()
            throws Exception {
        currentConversionMap = new HashMap<String, Activity>();
        currentFlowMap = new HashMap<BPMNNode, Set<BPMNNode>>();
    }

    @Test(expected = IllegalArgumentException.class)
    public void simplifyBPMNDiagram_withNullDiagram_expectedException() {
        Map<String, Activity> conversionMap = new HashMap<String, Activity>();

        simplifyBPMNDiagram(conversionMap, null);
    }

//    @Test(expected = IllegalArgumentException.class)
//    public void simplifyBPMNDiagram_withNullConversionMap_expectedException() {
//        simplifyBPMNDiagram(null, mock(BPMNDiagram.class));
//    }

    @Test
    public void simplifyBPMNDiagram_withDiagramNeedsSilentAndMerging_expectedCorrectSimplification() {
        BPMNDiagram diagram = createDiagramForCase1();

        simplifyBPMNDiagram(currentConversionMap, diagram);

        assertDiagramAndConversionMapForCase1(diagram);
    }

    private BPMNDiagram createDiagramForCase1() {
        // The diagram contains one silent activity and one XOR-gateway that should be merged with activity 'c'

        BPMNDiagram result = new BPMNDiagramImpl("BPMNDiagram with silent activity and merging needed");

        Event startEvent = addStartEvent(result);
        Event endEvent = addEndEvent(result);

        Activity a = addDefaultActivity(result, "a");
        Activity b = addDefaultActivity(result, "b");
        Activity c = addDefaultActivity(result, "c");
        Activity silent = addDefaultActivity(result, EMPTY_LABEL);

        addToConversionMap(a);
        addToConversionMap(b);
        addToConversionMap(c);
        addToConversionMap(silent);

        Gateway xor1 = result.addGateway("a_out", Gateway.GatewayType.DATABASED);
        Gateway xor2 = result.addGateway("c_in", Gateway.GatewayType.DATABASED);

        addFlow(result, startEvent, a);
        addFlow(result, a, xor1);
        addFlow(result, xor1, b);
        addFlow(result, xor1, silent);
        addFlow(result, b, xor2);
        addFlow(result, silent, xor2);
        addFlow(result, xor2, c);
        addFlow(result, c, endEvent);

        return result;
    }

    private Activity addDefaultActivity(BPMNDiagram diagram, String label) {
        return diagram.addActivity(label, DEFAULT_LOOPED, DEFAULT_AD_HOC, DEFAULT_COMPENSATION, DEFAULT_MULTI_INSTANCE,
                                   DEFAULT_COLLAPSED);
    }

    private Event addStartEvent(BPMNDiagram diagram) {
        return diagram
                .addEvent("start", Event.EventType.START, Event.EventTrigger.NONE, Event.EventUse.CATCH, true, null);
    }

    private Event addEndEvent(BPMNDiagram diagram) {
        return diagram.addEvent("end", Event.EventType.END, Event.EventTrigger.NONE, Event.EventUse.THROW, true, null);
    }

    private void addToConversionMap(Activity activity) {
        currentConversionMap.put(activity.getId().toString(), activity);
    }

    private void addFlow(BPMNDiagram diagram, BPMNNode source, BPMNNode target) {
        diagram.addFlow(source, target, "");
    }

    private void assertDiagramAndConversionMapForCase1(BPMNDiagram diagram) {
        Collection<Activity> activities = diagram.getActivities();
        Collection<Gateway> gateways = diagram.getGateways();
        Collection<Event> events = diagram.getEvents();
        Collection<Flow> flows = diagram.getFlows();

        // check number of elements
        assertEquals(3, activities.size());
        assertEquals(1, gateways.size());
        assertEquals(2, events.size());
        assertEquals(6, flows.size());

        // check persistence of activities
        Activity a = null, b = null, c = null;
        for (Activity activity : activities) {
            if ("a".equals(activity.getLabel())) a = activity;
            if ("b".equals(activity.getLabel())) b = activity;
            if ("c".equals(activity.getLabel())) c = activity;
        }
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);

        // check gateway
        Gateway xor = gateways.iterator().next();
        assertEquals(Gateway.GatewayType.DATABASED, xor.getGatewayType());
        assertEquals("a_out", xor.getLabel());

        // check events persistence
        Event start = null, end = null;
        for (Event event : events) {
            if ("start".equals(event.getLabel())) start = event;
            else if ("end".equals(event.getLabel())) end = event;
        }
        assertNotNull(start);
        assertNotNull(end);

        // check flows
        currentFlowMap.put(start, set(a));
        currentFlowMap.put(a, set(xor));
        currentFlowMap.put(xor, set(b, c));
        currentFlowMap.put(b, set(c));
        currentFlowMap.put(c, set(end));

        for (Flow flow : flows) {
            BPMNNode source = flow.getSource();
            BPMNNode target = flow.getTarget();
            assertTrue(currentFlowMap.get(source).contains(target));
        }
    }

    private Set<BPMNNode> set(BPMNNode... items) {
        Set<BPMNNode> result = new HashSet<BPMNNode>();
        Collections.addAll(result, items);
        return result;
    }

    @Test
    public void simplifyBPMNDiagram_withDiagramNeedsAllSimplifications_expectedCorrectSimplification() {
        BPMNDiagram diagram = createDiagramForCase2();

        simplifyBPMNDiagram(currentConversionMap, diagram);

        assertDiagramAndConversionMapForCase2(diagram);
    }

    private BPMNDiagram createDiagramForCase2() {
        // The diagram contains two silent activities and two XOR-gateways that should be merged with activities

        BPMNDiagram result = new BPMNDiagramImpl("BPMNDiagram with silent activities and merging and reducing needed");

        Event startEvent = addStartEvent(result);
        Event endEvent = addEndEvent(result);

        Activity a = addDefaultActivity(result, "a");
        Activity silent1 = addDefaultActivity(result, EMPTY_LABEL);
        Activity c = addDefaultActivity(result, "c");
        Activity silent2 = addDefaultActivity(result, EMPTY_LABEL);

        addToConversionMap(a);
        addToConversionMap(silent1);
        addToConversionMap(c);
        addToConversionMap(silent2);

        Gateway xor1 = result.addGateway("a_out", Gateway.GatewayType.DATABASED);
        Gateway xor2 = result.addGateway("c_in", Gateway.GatewayType.DATABASED);

        addFlow(result, startEvent, a);
        addFlow(result, a, xor1);
        addFlow(result, xor1, silent1);
        addFlow(result, xor1, silent2);
        addFlow(result, silent1, xor2);
        addFlow(result, silent2, xor2);
        addFlow(result, xor2, c);
        addFlow(result, c, endEvent);

        return result;
    }


    private void assertDiagramAndConversionMapForCase2(BPMNDiagram diagram) {
        Collection<Activity> activities = diagram.getActivities();
        Collection<Gateway> gateways = diagram.getGateways();
        Collection<Event> events = diagram.getEvents();
        Collection<Flow> flows = diagram.getFlows();

        // check number of elements
        assertEquals(2, activities.size());
        assertEquals(0, gateways.size());
        assertEquals(2, events.size());
        assertEquals(3, flows.size());

        // check persistence of activities
        Activity a = null, c = null;
        for (Activity activity : activities) {
            if ("a".equals(activity.getLabel())) a = activity;
            else if ("c".equals(activity.getLabel())) c = activity;
        }
        assertNotNull(a);
        assertNotNull(c);

        // check events persistence
        Event start = null, end = null;
        for (Event event : events) {
            if ("start".equals(event.getLabel())) start = event;
            else if ("end".equals(event.getLabel())) end = event;
        }
        assertNotNull(start);
        assertNotNull(end);

        // check flows
        currentFlowMap.put(start, set(a));
        currentFlowMap.put(a, set(c));
        currentFlowMap.put(c, set(end));

        for (Flow flow : flows) {
            BPMNNode source = flow.getSource();
            BPMNNode target = flow.getTarget();
            assertTrue(currentFlowMap.get(source).contains(target));
        }
    }

    @Test
    public void simplifyBPMNDiagram_withDiagramNeedsReducingAndMerging_expectedCorrectSimplification() {
        BPMNDiagram diagram = createDiagramForCase3();

        simplifyBPMNDiagram(currentConversionMap, diagram);

        assertDiagramAndConversionMapForCase3(diagram);
    }

    private BPMNDiagram createDiagramForCase3() {
        // Diagram contains two consequent and-gateways that should be reduced and then merged to activity a
        BPMNDiagram result = new BPMNDiagramImpl("label");

        Event startEvent = addStartEvent(result);
        Event endEvent = addEndEvent(result);

        Activity a = addDefaultActivity(result, "a");
        Activity b = addDefaultActivity(result, "b");
        Activity c = addDefaultActivity(result, "c");
        Activity d = addDefaultActivity(result, "d");
        Activity e = addDefaultActivity(result, "e");

        addToConversionMap(a);
        addToConversionMap(b);
        addToConversionMap(c);
        addToConversionMap(d);
        addToConversionMap(e);

        Gateway and1 = result.addGateway("and1", Gateway.GatewayType.PARALLEL);
        Gateway and2 = result.addGateway("and2", Gateway.GatewayType.PARALLEL);
        Gateway and3 = result.addGateway("and3", Gateway.GatewayType.PARALLEL);

        addFlow(result, startEvent, a);
        addFlow(result, a, and1);
        addFlow(result, and1, b);
        addFlow(result, and1, and2);
        addFlow(result, and1, c);
        addFlow(result, and2, d);
        addFlow(result, b, and3);
        addFlow(result, c, and3);
        addFlow(result, d, and3);
        addFlow(result, and3, e);
        addFlow(result, e, endEvent);

        return result;
    }

    private void assertDiagramAndConversionMapForCase3(BPMNDiagram diagram) {
        Collection<Activity> activities = diagram.getActivities();
        Collection<Gateway> gateways = diagram.getGateways();
        Collection<Event> events = diagram.getEvents();
        Collection<Flow> flows = diagram.getFlows();

        // check number of elements
        assertEquals(5, activities.size());
        assertEquals(1, gateways.size());
        assertEquals(2, events.size());
        assertEquals(9, flows.size());

        // check activities persistence
        Activity a = null, b = null, c = null, d = null, e = null;
        for (Activity activity : activities) {
            if ("a".equals(activity.getLabel())) a = activity;
            else if ("b".equals(activity.getLabel())) b = activity;
            else if ("c".equals(activity.getLabel())) c = activity;
            else if ("d".equals(activity.getLabel())) d = activity;
            else if ("e".equals(activity.getLabel())) e = activity;
            else fail("Unexpected activity label");
        }

        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(d);
        assertNotNull(e);

        // check gateway persistence
        Gateway and = gateways.iterator().next();
        assertNotNull(and);
        assertEquals(Gateway.GatewayType.PARALLEL, and.getGatewayType());
        assertEquals("and3", and.getLabel());


        // check events persistence
        Event start = null, end = null;
        for (Event event : events) {
            if ("start".equals(event.getLabel())) start = event;
            else if ("end".equals(event.getLabel())) end = event;
        }
        assertNotNull(start);
        assertNotNull(end);

        // check flows
        currentFlowMap.put(start, set(a));
        currentFlowMap.put(a, set(b, d, c));
        currentFlowMap.put(b, set(and));
        currentFlowMap.put(d, set(and));
        currentFlowMap.put(c, set(and));
        currentFlowMap.put(and, set(e));
        currentFlowMap.put(e, set(end));

        for (Flow flow : flows) {
            BPMNNode source = flow.getSource();
            BPMNNode target = flow.getTarget();
            assertTrue(currentFlowMap.get(source).contains(target));
        }
    }
}
