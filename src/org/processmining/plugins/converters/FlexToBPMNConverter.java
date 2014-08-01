package org.processmining.plugins.converters;

import java.util.*;

import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.FlexEdge;
import org.processmining.models.flexiblemodel.FlexNode;
import org.processmining.models.flexiblemodel.SetFlex;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramImpl;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;

public class FlexToBPMNConverter {

    private final Flex causalNet;
    private FlexNode startActivity;
    private FlexNode endActivity;

    private Map<FlexNode, Activity> activityMap;
    private Map<FlexEdge<? extends FlexNode, ? extends FlexNode>, BPMNNode> arcNodesOutputMap;
    private Map<FlexEdge<? extends FlexNode, ? extends FlexNode>, BPMNNode> arcNodesInputMap;

    private Map<String, Activity> conversionMap = new HashMap<String, Activity>();

    public FlexToBPMNConverter(Flex causalNet) {
        if (causalNet == null) throw new IllegalArgumentException("'causalNet' is null object");
        this.causalNet = causalNet;
    }

    public BPMNDiagram convert() {
        activityMap = new HashMap<FlexNode, Activity>();
        arcNodesOutputMap = new HashMap<FlexEdge<? extends FlexNode, ? extends FlexNode>, BPMNNode>();
        arcNodesInputMap = new HashMap<FlexEdge<? extends FlexNode, ? extends FlexNode>, BPMNNode>();
        checkCausalNet();
        BPMNDiagram result = new BPMNDiagramImpl(causalNet.getLabel());
        convertActivities(result);
        convertOutputBindings(result);
        convertInputBindings(result);
        addArcFlows(result);
        removeSyntheticStartAndEndActivities(result);
        return result;
    }

    private void checkCausalNet() {
        checkStartActivityExists(causalNet.getNodes());
        checkEndActivityExists(causalNet.getNodes());
    }

    private void checkStartActivityExists(Iterable<FlexNode> activities) {
        if (activities == null) throw new IllegalArgumentException("'activities' is null object");
        for (FlexNode activity : activities) {
            if (activity.getInputNodes() == null || activity.getInputNodes().isEmpty() ||
                activity.getInputNodes().size() == 1 && activity.getInputNodes().iterator().next().isEmpty()) {
                startActivity = activity;
                return;
            }
        }
        throw new IllegalArgumentException(
                "There is no start activity in the C-net. Conversion could not be performed");
    }

    private void checkEndActivityExists(Set<FlexNode> activities) {
        if (activities == null) throw new IllegalArgumentException("'activities' is null object");
        for (FlexNode activity : activities) {
            if (activity.getOutputNodes() == null || activity.getOutputNodes().isEmpty() ||
                activity.getOutputNodes().size() == 1 && activity.getOutputNodes().iterator().next().isEmpty()) {
                endActivity = activity;
                return;
            }
        }
        throw new IllegalArgumentException("There is no end activity in the C-net. Conversion could not be performed");

    }

    private void convertActivities(BPMNDiagram result) {
        for (FlexNode node : causalNet.getNodes()) {
            Activity activity = result.addActivity(node.getLabel(), false, false, false, false, false);
            activityMap.put(node, activity);
            conversionMap.put(node.getId().toString(), activity);
            if (node == startActivity) {
                Event startEvent =
                        result.addEvent("start", Event.EventType.START, Event.EventTrigger.NONE, Event.EventUse.CATCH,
                                        true, null);
                result.addFlow(startEvent, activity, "");
            }
            if (node == endActivity) {
                Event endEvent =
                        result.addEvent("end", Event.EventType.END, Event.EventTrigger.NONE, Event.EventUse.THROW, true,
                                        null);
                result.addFlow(activity, endEvent, "");
            }
        }
    }

    private void convertOutputBindings(BPMNDiagram result) {
        for (FlexNode node : activityMap.keySet()) {
            Set<SetFlex> outputs = node.getOutputNodes();
            BPMNNode current = activityMap.get(node);
            if (outputs.size() > 1) {
                Gateway xorGateway = result.addGateway(node.getLabel() + "_O", Gateway.GatewayType.DATABASED);
                result.addFlow(current, xorGateway, "");
                current = xorGateway;
            }

            int outputCounter = 0;
            Map<SetFlex, BPMNNode> outputsMap = new HashMap<SetFlex, BPMNNode>();

            for (SetFlex output : outputs) {
                BPMNNode bpmnOutput = current;
                if (output.size() > 1) {
                    Gateway andGateway =
                            result.addGateway(node.getLabel() + "_O" + outputCounter, Gateway.GatewayType.PARALLEL);
                    result.addFlow(current, andGateway, "");
                    bpmnOutput = andGateway;
                }
                outputCounter++;
                outputsMap.put(output, bpmnOutput);
            }

            Collection<FlexEdge<? extends FlexNode, ? extends FlexNode>> outgoingEdges = causalNet.getOutEdges(node);
            for (FlexEdge<? extends FlexNode, ? extends FlexNode> edge : outgoingEdges) {
                Collection<SetFlex> outputsWithTarget = getOutputsWithTarget(edge.getTarget(), node);

                if (outputsWithTarget.size() > 1) {
                    Gateway arcGateway = result.addGateway(node.getLabel() + "_" + (edge.getTarget()).getLabel(),
                                                           Gateway.GatewayType.DATABASED);

                    for (SetFlex output : outputsWithTarget)
                        result.addFlow(outputsMap.get(output), arcGateway, "");

                    arcNodesOutputMap.put(edge, arcGateway);
                }
                else if (outputsWithTarget.size() == 1) {
                    BPMNNode arcNode = outputsMap.get(outputsWithTarget.iterator().next());
                    arcNodesOutputMap.put(edge, arcNode);
                }
            }
        }
    }

    private List<SetFlex> getOutputsWithTarget(FlexNode target, FlexNode source) {
        List<SetFlex> result = new ArrayList<SetFlex>();
        for (SetFlex output : source.getOutputNodes()) {
            if (output.contains(target)) result.add(output);
        }
        return result;
    }

    private void convertInputBindings(BPMNDiagram result) {
        for (FlexNode node : activityMap.keySet()) {
            Set<SetFlex> inputs = node.getInputNodes();
            BPMNNode current = activityMap.get(node);
            if (inputs.size() > 1) {
                Gateway xorGateway = result.addGateway(node.getLabel() + "_I", Gateway.GatewayType.DATABASED);
                result.addFlow(xorGateway, current, "");
                current = xorGateway;
            }

            int inputsCounter = 0;
            Map<SetFlex, BPMNNode> inputsMap = new HashMap<SetFlex, BPMNNode>();

            for (SetFlex input : inputs) {
                BPMNNode bpmnInput = current;
                if (input.size() > 1) {
                    Gateway andGateway =
                            result.addGateway(node.getLabel() + "_I" + inputsCounter, Gateway.GatewayType.PARALLEL);
                    result.addFlow(andGateway, current, "");
                    bpmnInput = andGateway;
                }
                inputsCounter++;
                inputsMap.put(input, bpmnInput);
            }

            Collection<FlexEdge<? extends FlexNode, ? extends FlexNode>> ingoingEdges = causalNet.getInEdges(node);
            for (FlexEdge<? extends FlexNode, ? extends FlexNode> edge : ingoingEdges) {
                Collection<SetFlex> inputsWithSource = getInputsWithSource(edge.getSource(), node);

                if (inputsWithSource.size() > 1) {
                    Gateway arcGateway = result.addGateway((edge.getSource()).getLabel() + "_" + node.getLabel(),
                                                           Gateway.GatewayType.DATABASED);

                    for (SetFlex input : inputsWithSource)
                        result.addFlow(arcGateway, inputsMap.get(input), "");

                    arcNodesInputMap.put(edge, arcGateway);
                }
                else if (inputsWithSource.size() == 1) {
                    BPMNNode arcNode = inputsMap.get(inputsWithSource.iterator().next());
                    arcNodesInputMap.put(edge, arcNode);
                }
            }

        }
    }

    private Collection<SetFlex> getInputsWithSource(FlexNode source, FlexNode node) {
        List<SetFlex> result = new ArrayList<SetFlex>();
        for (SetFlex input : node.getInputNodes()) {
            if (input.contains(source)) result.add(input);
        }
        return result;
    }


    private void addArcFlows(BPMNDiagram result) {
        for (FlexEdge<? extends FlexNode, ? extends FlexNode> edge : causalNet.getEdges())
            if (arcNodesInputMap.containsKey(edge) && arcNodesOutputMap.containsKey(edge))
                result.addFlow(arcNodesOutputMap.get(edge), arcNodesInputMap.get(edge), "");

    }

    private void removeSyntheticStartAndEndActivities(BPMNDiagram diagram) {
        Activity start = conversionMap.get(startActivity.getId().toString());
        if (!"start".equals(start.getLabel())) return;
        Activity end = conversionMap.get(endActivity.getId().toString());
        if (!"end".equals(end.getLabel())) return;

        Flow startIn = null, startOut = null, endIn = null, endOut = null;
        for (Flow flow : diagram.getFlows()) {
            if (flow.getSource().equals(start)) startOut = flow;
            else if (flow.getTarget().equals(start)) startIn = flow;
            else if (flow.getSource().equals(end)) endOut = flow;
            else if (flow.getTarget().equals(end)) endIn = flow;
        }

        removeSyntheticActivity(start, startIn, startOut, diagram);
        removeSyntheticActivity(end, endIn, endOut, diagram);
    }

    private void removeSyntheticActivity(Activity activity, Flow inFlow, Flow outFlow, BPMNDiagram diagram) {
        if (inFlow == null || outFlow == null) return;

        BPMNNode predecessor = inFlow.getSource();
        BPMNNode successor = outFlow.getTarget();

        diagram.removeEdge(inFlow);
        diagram.removeEdge(outFlow);

        diagram.addFlow(predecessor, successor, "");
        diagram.removeActivity(activity);
    }

    public Flex getCausalNet() {
        return causalNet;
    }

    public Map<String, Activity> getConversionMap() {
        return conversionMap;
    }
}
