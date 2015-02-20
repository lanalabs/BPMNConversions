package org.processmining.tests.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.processmining.models.graphbased.directed.bpmn.elements.Gateway.GatewayType.DATABASED;
import static org.processmining.models.graphbased.directed.bpmn.elements.Gateway.GatewayType.PARALLEL;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.FlexImpl;
import org.processmining.models.flexiblemodel.FlexNode;
import org.processmining.models.flexiblemodel.SetFlex;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.plugins.converters.FlexFixer;
import org.processmining.plugins.converters.FlexToBPMNConverter;

public class FlexToBPMNConverterTest {

    @Test(expected = IllegalArgumentException.class)
    public void constructor_withNullCausalNet_expectedException() {
        new FlexToBPMNConverter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void convert_withNoStartActivity_expectedException() {
        Flex causalNet = createCNetWithNoStartActivity();
        FlexToBPMNConverter converter = new FlexToBPMNConverter(causalNet);
        converter.convert();
    }

    private Flex createCNetWithNoStartActivity() {
        Flex result = new FlexImpl("label");
        FlexNode b = result.addNode("b");
        FlexNode c = result.addNode("c");
        FlexNode d = result.addNode("d");

        SetFlex bInputs = new SetFlex();
        bInputs.add(c);
        SetFlex bOutputs = new SetFlex();
        bOutputs.add(c);
        bOutputs.add(d);
        b.addInputNodes(bInputs);
        b.addOutputNodes(bOutputs);

        SetFlex cInputs = new SetFlex();
        cInputs.add(b);
        SetFlex cOutputs = new SetFlex();
        cOutputs.add(b);
        c.addInputNodes(cInputs);
        c.addOutputNodes(cOutputs);

        SetFlex dInputs = new SetFlex();
        dInputs.add(b);
        d.addInputNodes(dInputs);

        result.addArc(c, b);
        result.addArc(b, c);
        result.addArc(b, d);

        return result;
    }

    @Test(expected = IllegalArgumentException.class)
    public void convert_withNoEndActivity_expectedException() {
        Flex causalNet = createCNetWithNoEndActivity();
        FlexToBPMNConverter converter = new FlexToBPMNConverter(causalNet);
        converter.convert();
    }

    private Flex createCNetWithNoEndActivity() {
        Flex result = new FlexImpl("label");
        FlexNode b = result.addNode("b");
        FlexNode c = result.addNode("c");
        FlexNode d = result.addNode("d");

        SetFlex bOutputs = new SetFlex();
        bOutputs.add(c);
        b.addOutputNodes(bOutputs);

        SetFlex cInput1 = new SetFlex();
        cInput1.add(b);
        SetFlex cInput2 = new SetFlex();
        cInput2.add(d);
        c.addInputNodes(cInput1);
        c.addInputNodes(cInput2);
        SetFlex cOutputs = new SetFlex();
        cOutputs.add(d);
        c.addOutputNodes(cOutputs);

        SetFlex dInputs = new SetFlex();
        dInputs.add(c);
        d.addInputNodes(dInputs);
        SetFlex dOutputs = new SetFlex();
        dOutputs.add(c);
        d.addOutputNodes(dOutputs);

        result.addArc(b, c);
        result.addArc(c, d);
        result.addArc(d, c);

        return result;
    }

    @Test(expected = IllegalArgumentException.class)
    public void convert_withNoActivities_expectedException() {
        Flex causalNet = new FlexImpl("label");
        FlexToBPMNConverter converter = new FlexToBPMNConverter(causalNet);
        converter.convert();
    }

    @Test
    public void convert_simpleExample_expectedCorrectConversion() {
        Flex causalNet = createSimpleCNet();
        FlexToBPMNConverter converter = new FlexToBPMNConverter(causalNet);

        BPMNDiagram bpmnDiagram = converter.convert();

        assertCorrectnessSimpleExample(bpmnDiagram);
    }

    private Flex createSimpleCNet() {
        Flex result = new FlexImpl("label");

        // nodes
        FlexNode a = result.addNode("a");
        FlexNode b = result.addNode("b");
        FlexNode c = result.addNode("c");
        FlexNode d = result.addNode("d");

        // arcs
        result.addArc(a, b);
        result.addArc(a, c);
        result.addArc(b, d);
        result.addArc(c, d);

        // outputs for a
        SetFlex aOut1 = new SetFlex();
        aOut1.add(b);
        SetFlex aOut2 = new SetFlex();
        aOut2.add(c);
        aOut2.add(b);
        a.addOutputNodes(aOut1);
        a.addOutputNodes(aOut2);

        // inputs for b
        SetFlex bIn = new SetFlex();
        bIn.add(a);
        b.addInputNodes(bIn);

        // outputs for b
        SetFlex bOut = new SetFlex();
        bOut.add(d);
        b.addOutputNodes(bOut);

        // inputs for c
        SetFlex cIn = new SetFlex();
        cIn.add(a);
        c.addInputNodes(cIn);

        // outputs for c
        SetFlex cOut = new SetFlex();
        cOut.add(d);
        c.addOutputNodes(cOut);

        // inputs for d
        SetFlex dIn1 = new SetFlex();
        dIn1.add(b);
        SetFlex dIn2 = new SetFlex();
        dIn2.add(b);
        dIn2.add(c);
        d.addInputNodes(dIn1);
        d.addInputNodes(dIn2);

        return result;
    }


    private void assertCorrectnessSimpleExample(BPMNDiagram bpmnDiagram) {
        assertEquals("label", bpmnDiagram.getLabel());

        // check activities existence
        Collection<Activity> activities = bpmnDiagram.getActivities();
        assertEquals(4, activities.size());

        Activity a = null, b = null, c = null, d = null;

        for (Activity activity : activities) {
            String label = activity.getLabel();
            if ("a".equals(label)) a = activity;
            if ("b".equals(label)) b = activity;
            if ("c".equals(label)) c = activity;
            if ("d".equals(label)) d = activity;
        }
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(d);

        // check events existence
        Collection<Event> events = bpmnDiagram.getEvents();
        assertEquals(2, events.size());

        Event startEvent = null, endEvent = null;
        for (Event event : events) {
            if (event.getEventType().equals(Event.EventType.START)) startEvent = event;
            if (event.getEventType().equals(Event.EventType.END)) endEvent = event;
        }
        assertNotNull(startEvent);
        assertNotNull(endEvent);

        // check gateways existence
        Collection<Gateway> gateways = bpmnDiagram.getGateways();
        assertEquals(6, gateways.size());

        Gateway aOutXor = null, aOutAnd = null, abXor = null, dInXor = null, dInAnd = null, bdXor = null;
        for (Gateway gateway : gateways) {
            if ("a_O".equals(gateway.getLabel()) && gateway.getGatewayType().equals(DATABASED)) aOutXor = gateway;
            if (gateway.getLabel().contains("a_O") && gateway.getGatewayType().equals(PARALLEL)) aOutAnd = gateway;
            if ("a_b".equals(gateway.getLabel()) && gateway.getGatewayType().equals(DATABASED)) abXor = gateway;
            if ("d_I".equals(gateway.getLabel()) && gateway.getGatewayType().equals(DATABASED)) dInXor = gateway;
            if (gateway.getLabel().contains("d_I") && gateway.getGatewayType().equals(PARALLEL)) dInAnd = gateway;
            if ("b_d".equals(gateway.getLabel()) && gateway.getGatewayType().equals(DATABASED)) bdXor = gateway;
        }
        assertNotNull(aOutXor);
        assertNotNull(aOutAnd);
        assertNotNull(abXor);
        assertNotNull(dInXor);
        assertNotNull(dInAnd);
        assertNotNull(bdXor);

        // check flow existence
        Collection<Flow> flows = bpmnDiagram.getFlows();
        assertEquals(14, flows.size());

        Map<BPMNNode, Set<BPMNNode>> expectedFlowsMap = new HashMap<BPMNNode, Set<BPMNNode>>();
        expectedFlowsMap.put(startEvent, createSetWithValues(a));
        expectedFlowsMap.put(a, createSetWithValues(aOutXor));
        expectedFlowsMap.put(aOutXor, createSetWithValues(abXor, aOutAnd));
        expectedFlowsMap.put(abXor, createSetWithValues(b));
        expectedFlowsMap.put(aOutAnd, createSetWithValues(abXor, c));
        expectedFlowsMap.put(b, createSetWithValues(bdXor));
        expectedFlowsMap.put(c, createSetWithValues(dInAnd));
        expectedFlowsMap.put(bdXor, createSetWithValues(dInAnd, dInXor));
        expectedFlowsMap.put(dInAnd, createSetWithValues(dInXor));
        expectedFlowsMap.put(dInXor, createSetWithValues(d));
        expectedFlowsMap.put(d, createSetWithValues(endEvent));

        for (Flow flow : flows) {
            Set<BPMNNode> possibleTargets = expectedFlowsMap.get(flow.getSource());
            if (!possibleTargets.contains(flow.getTarget()))
                fail("Inconsistent flow: " + flow.getSource().getLabel() + " - " + flow.getTarget().getLabel());
        }
    }

    private Set<BPMNNode> createSetWithValues(BPMNNode... values) {
        Set<BPMNNode> result = new HashSet<BPMNNode>();
        Collections.addAll(result, values);
        return result;
    }


    @Test
    public void convert_withMultipleStartEndActivities_expectedConversionWithoutStartEndActivities() {
        Set<SetFlex> startNodes = new HashSet<SetFlex>();
        Set<SetFlex> endNodes = new HashSet<SetFlex>();
        Flex causalNet = createCnetWithMultStartEndActivities(startNodes, endNodes);
        FlexFixer fixer = new FlexFixer(causalNet, startNodes, endNodes);
        fixer.fixMultipleInputsAndOutputs();

        BPMNDiagram diagram = new FlexToBPMNConverter(causalNet).convert();

        assertDiagramWithMultStartEndActivities(diagram);
    }

    private Flex createCnetWithMultStartEndActivities(Set<SetFlex> startNodes, Set<SetFlex> endNodes) {
        Flex result = new FlexImpl("label");

        FlexNode a = result.addNode("a");
        FlexNode b = result.addNode("b");
        FlexNode c = result.addNode("c");
        FlexNode d = result.addNode("d");
        FlexNode e = result.addNode("e");

        startNodes.add(setFlex(a));
        startNodes.add(setFlex(b));
        endNodes.add(setFlex(d));
        endNodes.add(setFlex(e));

        a.addOutputNodes(setFlex(c));
        b.addOutputNodes(setFlex(c));
        c.addInputNodes(setFlex(a));
        c.addInputNodes(setFlex(b));
        c.addOutputNodes(setFlex(d));
        c.addOutputNodes(setFlex(e));
        d.addInputNodes(setFlex(c));
        e.addInputNodes(setFlex(c));

        result.addArc(a, c);
        result.addArc(b, c);
        result.addArc(c, d);
        result.addArc(c, e);

        return result;
    }

    private SetFlex setFlex(FlexNode... nodes) {
        SetFlex result = new SetFlex();
        Collections.addAll(result, nodes);
        return result;
    }

    private void assertDiagramWithMultStartEndActivities(BPMNDiagram diagram) {
        // get collections
        Collection<Event> events = diagram.getEvents();
        Collection<Activity> activities = diagram.getActivities();
        Collection<Gateway> gateways = diagram.getGateways();
        Collection<Flow> flows = diagram.getFlows();

        // check sizes
        assertEquals(2, events.size());
        assertEquals(5, activities.size());
        assertEquals(4, gateways.size());
        assertEquals(12, flows.size());

        // check events
        Event start = null, end = null;
        for (Event event : events)
            if (event.getEventType().equals(Event.EventType.START)) start = event;
            else if (event.getEventType().equals(Event.EventType.END)) end = event;

        assertNotNull(start);
        assertNotNull(end);

        // check activities
        Activity a = null, b = null, c = null, d = null, e = null;
        for (Activity activity : activities)
            if ("a".equals(activity.getLabel())) a = activity;
            else if ("b".equals(activity.getLabel())) b = activity;
            else if ("c".equals(activity.getLabel())) c = activity;
            else if ("d".equals(activity.getLabel())) d = activity;
            else if ("e".equals(activity.getLabel())) e = activity;
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(d);
        assertNotNull(e);

        // check gateways
        Gateway start_O=null, c_I=null, c_O=null, end_I=null;
        for (Gateway gateway : gateways) {
            assertEquals(Gateway.GatewayType.DATABASED, gateway.getGatewayType());
            if ("start_O".equals(gateway.getLabel())) start_O = gateway;
            else if ("c_I".equals(gateway.getLabel())) c_I = gateway;
            else if ("c_O".equals(gateway.getLabel())) c_O = gateway;
            else if ("end_I".equals(gateway.getLabel())) end_I = gateway;
        }
        assertNotNull(start_O);
        assertNotNull(c_I);
        assertNotNull(c_O);
        assertNotNull(end_I);

        // check flows
        Map<BPMNNode, Set<BPMNNode>> flowMap = new HashMap<BPMNNode, Set<BPMNNode>>();
        flowMap.put(start, set(start_O));
        flowMap.put(start_O, set(a, b));
        flowMap.put(a, set(c_I));
        flowMap.put(b, set(c_I));
        flowMap.put(c_I, set(c));
        flowMap.put(c, set(c_O));
        flowMap.put(c_O, set(d, e));
        flowMap.put(e, set(end_I));
        flowMap.put(d, set(end_I));
        flowMap.put(end_I, set(end));

        for (Flow flow : flows)
            assertTrue(flowMap.get(flow.getSource()).contains(flow.getTarget()));
    }

    private Set<BPMNNode> set(BPMNNode ... nodes) {
        Set<BPMNNode> result = new HashSet<BPMNNode>();
        Collections.addAll(result, nodes);
        return result;
    }
}
