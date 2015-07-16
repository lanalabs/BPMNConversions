package org.processmining.plugins.converters;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

public class FlexToBPMNWithORConverter {

		private final Flex causalNet;
	    private FlexNode startActivity;
	    private FlexNode endActivity;

	    private Map<FlexNode, Activity> activityMap;
	    private Map<FlexNode, BPMNNode> NodesOutputMap;
	    private Map<FlexNode, BPMNNode> NodesInputMap;

	    private Map<String, Activity> conversionMap = new HashMap<String, Activity>();

	    public FlexToBPMNWithORConverter(Flex causalNet) {
	        if (causalNet == null) throw new IllegalArgumentException("'causalNet' is null object");
	        this.causalNet = causalNet;
	    }

	    public BPMNDiagram convert() {
	        activityMap = new HashMap<FlexNode, Activity>();
	        NodesOutputMap = new HashMap<FlexNode, BPMNNode>();
	        NodesInputMap = new HashMap<FlexNode, BPMNNode>();
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
				boolean withOR = false;
				if (outputs.size() > 1) {
					for (SetFlex output : outputs) {						
						if (output.size() > 1) {
							Gateway orGateway = result.addGateway(node.getLabel() + "_OUT",
									Gateway.GatewayType.INCLUSIVE);
							result.addFlow(current, orGateway, "");
							current = orGateway;
							withOR=true;
							break;
						}
					}
					if(!withOR) {
						Gateway xorGateway = result.addGateway(node.getLabel() + "_OUT", Gateway.GatewayType.DATABASED);
						result.addFlow(current, xorGateway, "");
						current = xorGateway;
					}
				} else { // if outputs.size() == 1
					if(outputs.iterator().hasNext()) {
						SetFlex output = outputs.iterator().next();
						if(output.size() > 1) {
							Gateway andGateway = result.addGateway(node.getLabel() + "_OUT", Gateway.GatewayType.PARALLEL);
							result.addFlow(current, andGateway, "");
							current = andGateway;
						}
					}
				}
				NodesOutputMap.put(node, current);
			}	
		}

	    private void convertInputBindings(BPMNDiagram result) {
	    	for (FlexNode node : activityMap.keySet()) {
				Set<SetFlex> inputs = node.getInputNodes();
				BPMNNode current = activityMap.get(node);
				boolean withOR = false;
				if (inputs.size() > 1) {
					for (SetFlex input : inputs) {						
						if (inputs.size() > 1) {
							Gateway orGateway = result.addGateway(node.getLabel() + "_IN",
									Gateway.GatewayType.INCLUSIVE);
							result.addFlow(orGateway, current, "");
							current = orGateway;
							withOR=true;
							break;
						}
					}
					if(!withOR) {
						Gateway xorGateway = result.addGateway(node.getLabel() + "_IN", Gateway.GatewayType.DATABASED);
						result.addFlow(xorGateway, current, "");
						current = xorGateway;
					}
				} else { // if inputs.size() == 1
					if(inputs.iterator().hasNext()) {
					SetFlex input = inputs.iterator().next();
					if(input.size() > 1) {
						Gateway andGateway = result.addGateway(node.getLabel() + "_IN", Gateway.GatewayType.PARALLEL);
						result.addFlow(andGateway, current, "");
						current = andGateway;
					}
					}
				}
				NodesInputMap.put(node, current);
			}	
	    }

	    private void addArcFlows(BPMNDiagram result) {
	        for (FlexEdge<? extends FlexNode, ? extends FlexNode> edge : causalNet.getEdges()) {
	           FlexNode source =  edge.getSource();
	           FlexNode target =  edge.getTarget();
	        	if (NodesInputMap.containsKey(target) && NodesOutputMap.containsKey(source)) {
	                result.addFlow(NodesOutputMap.get(source), NodesInputMap.get(target), "");
	        	}
	        }
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
