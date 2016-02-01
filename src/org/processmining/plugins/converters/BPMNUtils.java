package org.processmining.plugins.converters;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway.GatewayType;

/**
 * Standard BPMN transformations
 * 
 *
 * @author Anna Kalenkova
 * May 20, 2014
 */
public class BPMNUtils {

	public static final String EMPTY = "Empty";
	
	/**
	 * Simplify BPMN diagram
	 * 
	 * @param conversionMap
	 * @param diagram
	 */
	public static void simplifyBPMNDiagram(Map<String, Activity> conversionMap, BPMNDiagram diagram) {
        if (diagram == null) throw new IllegalArgumentException("'conversionMap' is null");

        removeSilentActivities(conversionMap, diagram);
        reduceGateways(diagram);
		mergeActivitiesAndGateways(diagram);
		removeSuperfluousGateways(diagram);
	}
	
	/**
	 * Remove superfluous gateways
	 * 
	 * @param diagram
	 */
	private static void removeSuperfluousGateways(BPMNDiagram diagram) {
		Set<Gateway> gatewaysToRemove = new HashSet<Gateway>();
		for (Gateway gateway : diagram.getGateways()) {
			if ((diagram.getInEdges(gateway).size() == 1) && (diagram.getOutEdges(gateway).size() == 1)) {
				BPMNNode inNode = diagram.getInEdges(gateway).iterator().next().getSource();
				BPMNNode outNode = diagram.getOutEdges(gateway).iterator().next().getTarget();
				gatewaysToRemove.add(gateway);
				diagram.addFlow(inNode, outNode, "");
			}
		}
		for (Gateway gateway : gatewaysToRemove) {
			diagram.removeGateway(gateway);
		}
	}
	
	/**
	 * Reduce gateways
	 * 
	 * @param diagram
	 */
	private static void reduceGateways(BPMNDiagram diagram) {
		boolean diagramChanged;
		do {
			diagramChanged = false;
			Gateway gatewayToRemove = null;
			for (Gateway gateway : diagram.getGateways()) {
				for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> flow : diagram.getOutEdges(gateway)) {
					if (flow.getTarget() instanceof Gateway) {
						Gateway followingGateway = (Gateway) flow.getTarget();
						if ((diagram.getOutEdges(gateway).size() == 1)
								|| (diagram.getInEdges(followingGateway).size() == 1)) {
							if (gateway.getGatewayType().equals(followingGateway.getGatewayType())) {
								Collection<BPMNNode> followingNodes = new HashSet<BPMNNode>();
								for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> outFlow : diagram
										.getOutEdges(followingGateway)) {
									BPMNNode followingNode = outFlow.getTarget();
									followingNodes.add(followingNode);
								}
								Collection<BPMNNode> precNodes = new HashSet<BPMNNode>();
								for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> inFlow : diagram
										.getInEdges(followingGateway)) {
									BPMNNode precNode = inFlow.getSource();
									if(!gateway.equals(precNode)) {
										precNodes.add(precNode);
									}
								}
								for (BPMNNode followingNode : followingNodes) {
									addFlow(diagram, gateway, followingNode);
								}
								for (BPMNNode precNode : precNodes) {
									addFlow(diagram, precNode, gateway);
								}
								gatewayToRemove = followingGateway;
								diagramChanged = true;
								break;
							}
						}
					}
				}
				if(diagramChanged) {
					break;
				}
			}
			if(diagramChanged) {
				diagram.removeGateway(gatewayToRemove);
			}
		} while (diagramChanged);
	}

	/**
	 * Merge activities and gateways
	 * 
	 * @param diagram
	 */
	private static void mergeActivitiesAndGateways(BPMNDiagram diagram) {
		for (Activity activity : diagram.getActivities()) {
			//if (numberOfOutgoingSequenceFlows(activity, diagram) == 1) {
				for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> flow : diagram.getOutEdges(activity)) {
					if (flow.getTarget() instanceof Gateway) {
						Gateway followingGateway = (Gateway) flow.getTarget();
						if (GatewayType.PARALLEL.equals(followingGateway.getGatewayType())) {
							if (diagram.getInEdges(followingGateway).size() == 1) {
								Collection<BPMNNode> followingNodes = new HashSet<BPMNNode>();
								for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> outFlow : diagram
										.getOutEdges(followingGateway)) {
									BPMNNode followingNode = outFlow.getTarget();
									followingNodes.add(followingNode);
								}
								diagram.removeGateway(followingGateway);
								for (BPMNNode followingNode : followingNodes) {
									addFlow(diagram, activity, followingNode);

								}
							}
						}
					}
				//}
			}

			for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> flow : diagram.getInEdges(activity)) {
				//if (numberOfIncomingSequenceFlows(activity, diagram) == 1) {
					if (flow.getSource() instanceof Gateway) {
						Gateway precedingGateway = (Gateway) flow.getSource();
						if (GatewayType.DATABASED.equals(precedingGateway.getGatewayType())) {
							if (diagram.getOutEdges(precedingGateway).size() == 1) {
								Collection<BPMNNode> precedingNodes = new HashSet<BPMNNode>();
								for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> inFlow : diagram
										.getInEdges(precedingGateway)) {
									BPMNNode precedingNode = inFlow.getSource();
									precedingNodes.add(precedingNode);
								}
								diagram.removeGateway(precedingGateway);
								for (BPMNNode precedingNode : precedingNodes) {
									addFlow(diagram, precedingNode, activity);

								}
							}
						}
					}
				//}
			}
		}
	}
	
	
	/**
	 * Get the number of outgoing flows
	 * @param node
	 * @param diagram
	 * @return
	 */
	public static int numberOfOutgoingSequenceFlows(BPMNNode node, BPMNDiagram diagram) {
		int result = 0;
		for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> flow : diagram.getOutEdges(node)) {
			if (flow instanceof Flow) {
				result++;
			}
		}
		return result;
	}

	/**
	 * Get the number of incoming flows
	 * @param node
	 * @param diagram
	 * @return
	 */
	public static int numberOfIncomingSequenceFlows(BPMNNode node, BPMNDiagram diagram) {
		int result = 0;
		for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> flow : diagram.getInEdges(node)) {
			if (flow instanceof Flow) {
				result++;
			}
		}
		return result;
	}
	
	/**
	 * Remove silent activities
	 * 
	 * @param diagram
	 */
	private static void removeSilentActivities(Map<String, Activity> conversionMap, BPMNDiagram diagram) {
		Collection<Activity> allActivities = new HashSet<Activity>();
		allActivities.addAll(diagram.getActivities());
		for (Activity activity : allActivities) {
			if (EMPTY.equals(activity.getLabel())) {
				Collection<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>> inEdges = diagram.getInEdges(activity);
				Collection<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>> outEdges = diagram.getOutEdges(activity);
				if ((inEdges.iterator().hasNext()) && (outEdges.iterator().hasNext())) {
					BPMNNode source = inEdges.iterator().next().getSource();
					BPMNNode target = outEdges.iterator().next().getTarget();
					addFlow(diagram, source, target);
				}
				diagram.removeActivity(activity);
				
				if (conversionMap != null) {
					Set<String> idToRemove = new HashSet<String>();
					for (String id : conversionMap.keySet()) {
						if (activity == conversionMap.get(id)) {
							idToRemove.add(id);
						}
					}
					for (String id : idToRemove) {
						conversionMap.remove(id);
					}
				}
			}
		}
	}

    private static Flow addFlow(BPMNDiagram diagram, BPMNNode source, BPMNNode target) {
        for (BPMNEdge flow : diagram.getEdges())
            if (source == flow.getSource() && target == flow.getTarget()) return null;
        return diagram.addFlow(source, target, "");
    }
}
