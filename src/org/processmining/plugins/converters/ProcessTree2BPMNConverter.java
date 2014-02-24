package org.processmining.plugins.converters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.NodeID;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramImpl;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventTrigger;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventType;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventUse;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway.GatewayType;
import org.processmining.models.graphbased.directed.bpmn.elements.Swimlane;
import org.processmining.models.graphbased.directed.bpmn.elements.SwimlaneType;
import org.processmining.processtree.Block;
import org.processmining.processtree.Event;
import org.processmining.processtree.Event.Message;
import org.processmining.processtree.Event.TimeOut;
import org.processmining.processtree.Node;
import org.processmining.processtree.Originator;
import org.processmining.processtree.ProcessTree;
import org.processmining.processtree.Task;
import org.processmining.processtree.Task.Manual;

/**
 * Converts a process tree to BPMN model
 *
 * @author Anna Kalenkova
 * Oct 01, 2013
 */
@Plugin(name = "Convert Process tree to BPMN diagram", parameterLabels = { "Process tree" }, 
returnLabels = { "BPMN Diagram,", "Conversion map" }, returnTypes = { BPMNDiagram.class, Map.class }, 
userAccessible = true, help = "Converts Process tree to BPMN diagram")
public class ProcessTree2BPMNConverter {
	
	private static final String PROCESS_TREE_INTERNAL_NODE = "Process tree internal node";
	
	private String currentLabel ="";
	
	private Map<UUID, Swimlane> orgIdMap = new HashMap<UUID, Swimlane>();
	
	private Map<BPMNNode, Node> conversionMap = new HashMap<BPMNNode, Node>();
	
	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Convert Process tree to BPMN", requiredParameterLabels = { 0 })
	public Object[] convert(UIPluginContext context, ProcessTree tree) {
		
		Progress progress = context.getProgress();
		progress.setCaption("Converting Process tree To BPMN diagram");
		
		BPMNDiagram bpmnDiagram = new BPMNDiagramImpl("BPMN diagram for " 
				+ tree.getName());
		
		// Convert originators
		convertOriginators(tree, bpmnDiagram);

		// Convert Process tree to a BPMN diagram
		convert(tree, bpmnDiagram);
		
		progress.setCaption("Getting BPMN Visualization");
		
		Map<NodeID, UUID> idMap = retrieveIdMap();
		return new Object[] {bpmnDiagram, idMap};
	}
	
	/**
	 * Constructs IdMap from ConversionMap
	 * 
	 * @return
	 */
	private Map<NodeID, UUID> retrieveIdMap() {
		Map<NodeID, UUID> idMap = new HashMap<NodeID, UUID>();
		for(BPMNNode bpmnNode : conversionMap.keySet()) {
			idMap.put(bpmnNode.getId(), conversionMap.get(bpmnNode).getID());
		}
		for(UUID org: orgIdMap.keySet()){
            idMap.put(orgIdMap.get(org).getId(), org);
		}
		return idMap;
	}
	
	/**
	 * Convert originators
	 * 
	 * @param tree
	 * @param bpmnDiagram
	 */
	private void convertOriginators(ProcessTree tree, BPMNDiagram bpmnDiagram) {
		// Add lanes
		for(Originator originator : tree.getOriginators()) {
			Swimlane lane = bpmnDiagram.addSwimlane(originator.getName(), null, SwimlaneType.LANE);
			orgIdMap.put(originator.getID(), lane);
		}		
	}
	
	/**
	 * Converts process tree to BPMN diagram
	 * 
	 * @param tree
	 * @param bpmnDiagram
	 * @return
	 */
	private void convert(ProcessTree tree, BPMNDiagram bpmnDiagram) {

		// Create initial elements
		org.processmining.models.graphbased.directed.bpmn.elements.Event startEvent 
			= bpmnDiagram.addEvent("Start", EventType.START, null, null, null);
		org.processmining.models.graphbased.directed.bpmn.elements.Event endEvent 
			= bpmnDiagram.addEvent("End", EventType.END, null, null, null);
		Activity rootActivity = 
				bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, false, false, false, false);
		bpmnDiagram.addFlow(startEvent, rootActivity, "");
		bpmnDiagram.addFlow(rootActivity, endEvent, "");
		
		conversionMap.put(rootActivity, tree.getRoot());
		expandNodes(tree, bpmnDiagram);
	}
	
	/**
	 * Expand all activities which correspond to the internal process tree nodes
	 * 
	 * @param tree
	 * @param bpmnDiagram
	 */
	private void expandNodes( ProcessTree tree, BPMNDiagram bpmnDiagram) {
		Activity activity = takeFirstInternalActivity();
		if (activity != null) {
			if (PROCESS_TREE_INTERNAL_NODE.equals(activity.getLabel())) {
				Node treeNode = conversionMap.get(activity);
				if (treeNode instanceof Task) {
					expandTask(activity, (Task) treeNode, tree, bpmnDiagram);
				} else if (treeNode instanceof Event) {
					expandEvent(activity, (Event) treeNode, tree, bpmnDiagram);
				} else if (treeNode instanceof Block) {
					expandBlock(activity, (Block) treeNode, tree, bpmnDiagram);
				}
			}
		}
	}
	
	/**
	 * Take first internal activity from conversion map
	 * 
	 * @return
	 */
	private Activity takeFirstInternalActivity() {
		Activity activity = null;
		if (!conversionMap.isEmpty()) {
			for (BPMNNode bpmnNode : conversionMap.keySet()) {
				if(bpmnNode instanceof Activity) {
					if(PROCESS_TREE_INTERNAL_NODE.equals(bpmnNode.getLabel())) {
						activity = (Activity)bpmnNode;
					}
				}
			}
		}
		return activity;
	}
	
	/**
	 * Expand activity which corresponds to the tree internal node
	 * 
	 * @param activity
	 * @param blockNode
	 * @param tree
	 * @param bpmnDiagram
	 */
	@SuppressWarnings("incomplete-switch")
	private void expandBlock(Activity activity, Block blockNode, ProcessTree tree, BPMNDiagram bpmnDiagram) {	
		switch(tree.getType(blockNode)) {
			case XOR: {
				expandGate(activity, blockNode, tree, bpmnDiagram, GatewayType.DATABASED);
				break;
			}
			case OR: {
				expandGate(activity, blockNode, tree, bpmnDiagram, GatewayType.INCLUSIVE);
				break;
			}
			case AND : {
				expandGate(activity, blockNode, tree, bpmnDiagram, GatewayType.PARALLEL);
				break;
			}
			case SEQ : {
				expandSequence(activity, blockNode, tree, bpmnDiagram);
				break;
			}
			case DEF : {
				expandGate(activity, blockNode, tree, bpmnDiagram, GatewayType.EVENTBASED);
				break;
			}
			case LOOPXOR : {
				expandLoop(activity, blockNode, tree, bpmnDiagram, false);
				break;
			}
			case LOOPDEF : {
				expandLoop(activity, blockNode, tree, bpmnDiagram, true);
				break;
			}
			case PLACEHOLDER : {
				expandPlaceholder(activity, blockNode, tree, bpmnDiagram);
			}
		}
		conversionMap.remove(activity);
		expandNodes(tree, bpmnDiagram);
	}
	
	/**
	 * Expand activity which corresponds to the task
	 * 
	 * @param activity
	 * @param taskNode
	 * @param tree
	 * @param bpmnDiagram
	 */
	private void expandTask(Activity activity, Task taskNode, ProcessTree tree, BPMNDiagram bpmnDiagram) {	
		
		// Delete activity and corresponding incoming and outgoing flows
		BPMNNode source = deleteIncomingFlow(activity, bpmnDiagram);
		BPMNNode target = deleteOutgoingFlow(activity, bpmnDiagram);
		bpmnDiagram.removeActivity(activity);
		
		// Add new task
		Activity task = bpmnDiagram.addActivity(taskNode.getName(), false, false, false,
				false, false);
		
		bpmnDiagram.addFlow(source, task, currentLabel);		                                                                                                                                            
		bpmnDiagram.addFlow(task, target, "");	
		
		conversionMap.remove(activity);
		conversionMap.put(task, taskNode);
		if(taskNode instanceof Manual) {
			Manual manualTask = (Manual)taskNode;
			Collection<Originator> originators = manualTask.getOriginators();
			if(originators.size() == 1) {
				Originator originator = originators.iterator().next();
				Swimlane lane = orgIdMap.get(originator.getID());
				task.setParentSwimlane(lane);
			}
		}
		expandNodes(tree, bpmnDiagram);
	}
	
	/**
	 * Expand activity which corresponds to the event
	 * 
	 * @param activity
	 * @param eventNode
	 * @param tree
	 * @param bpmnDiagram
	 */
	private void expandEvent(Activity activity, Event eventNode, ProcessTree tree, BPMNDiagram bpmnDiagram) {	
		
		// Delete activity and corresponding incoming and outgoing flows
		BPMNNode source = deleteIncomingFlow(activity, bpmnDiagram);
		BPMNNode target = deleteOutgoingFlow(activity, bpmnDiagram);
		bpmnDiagram.removeActivity(activity);
		
		EventTrigger eventTrigger = null;
		if(eventNode instanceof TimeOut) {
			eventTrigger = EventTrigger.TIMER;
		} else if(eventNode instanceof Message) {
			eventTrigger = EventTrigger.SIGNAL;
		}
		// Add new event
		org.processmining.models.graphbased.directed.bpmn.elements.Event event 
			= bpmnDiagram.addEvent(eventNode.getMessage(), EventType.INTERMEDIATE, eventTrigger,
					EventUse.CATCH, null);
		
		bpmnDiagram.addFlow(source, event, currentLabel);	
		// Add child
		if(eventNode.getChildren().size() != 1) {
			throw new ConverterException("Event node must have one children");
		}
		Node child = eventNode.getChildren().get(0);
		Activity newActivity = bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, false, false, false, false);
		bpmnDiagram.addFlow(event, newActivity, "");
		bpmnDiagram.addFlow(newActivity, target, "");
		conversionMap.put(newActivity, child);
		
		conversionMap.remove(activity);
		expandNodes(tree, bpmnDiagram);
	}
	
	/**
	 * Expand gate node
	 * 
	 * @param activity
	 * @param blockNode
	 * @param tree
	 * @param bpmnDiagram
	 * @param gatewayType
	 */
	private void expandGate(Activity activity, Block blockNode, ProcessTree tree,
			BPMNDiagram bpmnDiagram, GatewayType gatewayType) {
		
		// Delete activity and corresponding incoming and outgoing flows
		BPMNNode source = deleteIncomingFlow(activity, bpmnDiagram);
		BPMNNode target = deleteOutgoingFlow(activity, bpmnDiagram);
		bpmnDiagram.removeActivity(activity);
		
		Gateway split = bpmnDiagram.addGateway("", gatewayType);
		if(gatewayType.equals(GatewayType.EVENTBASED)) {
			gatewayType = GatewayType.DATABASED;
		}
		conversionMap.put(split, blockNode);
		Gateway join = bpmnDiagram.addGateway("", gatewayType);
		bpmnDiagram.addFlow(source, split, currentLabel);		                                                                                                                                            
		bpmnDiagram.addFlow(join, target, "");	
		
		// Add new activities
		for(Node child : blockNode.getChildren()) {
			Activity newActivity  = bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, 
					false, false, false, false);
			bpmnDiagram.addFlow(split, newActivity, "");
			bpmnDiagram.addFlow(newActivity, join, "");
			conversionMap.put(newActivity, child);
		}
	}
	
	/**
	 * Expand sequence node
	 * 
	 * @param activity
	 * @param blockNode
	 * @param tree
	 * @param bpmnDiagram
	 */
	private void expandSequence( Activity activity, Block blockNode, ProcessTree tree, BPMNDiagram bpmnDiagram) {
		
		// Delete activity and corresponding incoming and outgoing flows
		BPMNNode source = deleteIncomingFlow(activity, bpmnDiagram);
		BPMNNode target = deleteOutgoingFlow(activity, bpmnDiagram);
		bpmnDiagram.removeActivity(activity);
		
		BPMNNode prevNode = source;
		// Add new activities
		for(Node child : blockNode.getChildren()) {
			Activity newActivity  = bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, 
					false, false, false, false);
			bpmnDiagram.addFlow(prevNode, newActivity, currentLabel);
			conversionMap.put(newActivity, child);
			prevNode = newActivity;
		}
		bpmnDiagram.addFlow(prevNode, target, "");
	}
	
//	/**
//	 * Expand deferred choice
//	 * 
//	 * @param conversionMap
//	 * @param activity
//	 * @param blockNode
//	 * @param tree
//	 * @param bpmnDiagram
//	 * @param gatewayType
//	 */
//	private void expandDefChoice(Map<Activity, Node> conversionMap, Activity activity, Block blockNode, 
//			ProcessTree tree, BPMNDiagram bpmnDiagram) {
//		
//		// Delete activity and corresponding incoming and outgoing flows
//		BPMNNode source = deleteIncomingFlow(activity, bpmnDiagram);
//		BPMNNode target = deleteOutgoingFlow(activity, bpmnDiagram);
//		bpmnDiagram.removeActivity(activity);
//		
//		Gateway eventBasedGateway = bpmnDiagram.addGateway("", GatewayType.EVENTBASED);
//		Gateway xorJoin = bpmnDiagram.addGateway("", GatewayType.DATABASED);
//		bpmnDiagram.addFlow(source, eventBasedGateway, "");		                                                                                                                                            
//		bpmnDiagram.addFlow(xorJoin, target, "");	
//		
//		// Add new activities
//		for(Node child : blockNode.getChildren()) {
//			Event catchSignalEvent  = 
//					bpmnDiagram.addEvent("", EventType.INTERMEDIATE, EventTrigger.SIGNAL, EventUse.CATCH, null);
//			bpmnDiagram.addFlow(eventBasedGateway, catchSignalEvent, "");
//			Activity newActivity  = bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, 
//					false, false, false, false);
//			bpmnDiagram.addFlow(catchSignalEvent, newActivity, "");
//			bpmnDiagram.addFlow(newActivity, xorJoin, "");
//			conversionMap.put(newActivity, child);
//		}
//	}
	
	/**
	 * Expand loop
	 * 
	 * @param activity
	 * @param blockNode
	 * @param tree
	 * @param bpmnDiagram
	 * @param gatewayType
	 * @param isDeferred
	 */
	private void expandLoop(Activity activity, Block blockNode, ProcessTree tree,
			BPMNDiagram bpmnDiagram, boolean isDeferred) {
		
		// Delete activity and corresponding incoming and outgoing flows
		BPMNNode source = deleteIncomingFlow(activity, bpmnDiagram);
		BPMNNode target = deleteOutgoingFlow(activity, bpmnDiagram);
		bpmnDiagram.removeActivity(activity);
			
		Gateway xorJoin = bpmnDiagram.addGateway("", GatewayType.DATABASED);
		Gateway xorSplit = null;
		if (isDeferred) {
			xorSplit = bpmnDiagram.addGateway("", GatewayType.EVENTBASED);
		} else {
			xorSplit = bpmnDiagram.addGateway("", GatewayType.DATABASED);
		}
		
		conversionMap.put(xorSplit, blockNode);
		bpmnDiagram.addFlow(source, xorJoin, currentLabel);		                                                                                                                                            
		
		// Add loop body
		if(blockNode.getChildren().size() != 3) {
			throw new ConverterException("Loop node must have three children");
		}
		Node child1 = blockNode.getChildren().get(0);
		Activity newActivity1 = bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, false,
				false, false, false);
		bpmnDiagram.addFlow(xorJoin, newActivity1, "");
		bpmnDiagram.addFlow(newActivity1, xorSplit, "");
		conversionMap.put(newActivity1, child1);
		
		Node child2 = blockNode.getChildren().get(1);
		Activity newActivity2 = bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, false,
				false, false, false);
		bpmnDiagram.addFlow(xorSplit, newActivity2, "");
		bpmnDiagram.addFlow(newActivity2, xorJoin, "");
		conversionMap.put(newActivity2, child2);
		
		Node child3 = blockNode.getChildren().get(2);
		Activity newActivity3 = bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, false,
				false, false, false);
		bpmnDiagram.addFlow(xorSplit, newActivity3, "");
		bpmnDiagram.addFlow(newActivity3, target, "");
		conversionMap.put(newActivity3, child3);
	}
	
	/**
	 * Expand placeholder
	 * 
	 * @param activity
	 * @param blockNode
	 * @param tree
	 * @param bpmnDiagram
	 */
	private void expandPlaceholder(Activity activity, Block blockNode, ProcessTree tree,
			BPMNDiagram bpmnDiagram) {
		
		// Delete activity and corresponding incoming and outgoing flows
		BPMNNode source = deleteIncomingFlow(activity, bpmnDiagram);
		BPMNNode target = deleteOutgoingFlow(activity, bpmnDiagram);
		bpmnDiagram.removeActivity(activity);
		
		Gateway split = bpmnDiagram.addGateway("Placeholder", GatewayType.DATABASED);
		Gateway join = bpmnDiagram.addGateway("", GatewayType.DATABASED);
		bpmnDiagram.addFlow(source, split, currentLabel);		                                                                                                                                            
		bpmnDiagram.addFlow(join, target, "");
		
		// Add new activities
		int childNum = 1;
		for(Node child : blockNode.getChildren()) {
			Activity newActivity  = bpmnDiagram.addActivity(PROCESS_TREE_INTERNAL_NODE, false, 
					false, false, false, false);
			String label = "";
			if(childNum == 1) {
				label = "This subprocess could be replaced by one of the alternatives";
			} else {
				label = "Alternative " + childNum;
			}
			bpmnDiagram.addFlow(split, newActivity, label);
			bpmnDiagram.addFlow(newActivity, join, "");
			conversionMap.put(newActivity, child);
			childNum++;
		}
	}
	
	/**
	 * Delete incoming flow of the activity, if activity has more than one incoming flow,
	 * exception will be generated
	 * 
	 * @param activity
	 * @param bpmnDiagram
	 * @return not null source node of the deleted incoming flow
	 */
	private BPMNNode deleteIncomingFlow(Activity activity, BPMNDiagram bpmnDiagram) {
		Collection<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>> incomingFlows 
			= bpmnDiagram.getInEdges(activity);
		BPMNNode source = null;
		if (incomingFlows.size() == 1) {
			BPMNEdge<? extends BPMNNode, ? extends BPMNNode> incomingFlow = incomingFlows.iterator().next();
			source = incomingFlow.getSource();
			currentLabel = incomingFlow.getLabel();
			bpmnDiagram.removeEdge(incomingFlow);
		} else {
			throw new ConverterException("Expanded activity has many incomng control flows");
		}
		
		return source;
	}
	
	/**
	 * Delete outgoing flow of the activity, if activity has more than one outgoing flow,
	 * exception will be generated
	 * 
	 * @param activity
	 * @param bpmnDiagram
	 * @return not null target node of the deleted outgoing flow
	 */
	private BPMNNode deleteOutgoingFlow(Activity activity, BPMNDiagram bpmnDiagram) {
		Collection<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>> outgoingFlows 
			= bpmnDiagram.getOutEdges(activity);
		BPMNNode target = null;
		if (outgoingFlows.size() == 1) {
			BPMNEdge<? extends BPMNNode, ? extends BPMNNode> outgoingFlow = outgoingFlows.iterator().next();
			target = outgoingFlow.getTarget();
			bpmnDiagram.removeEdge(outgoingFlow);
		} else {
			throw new ConverterException("Expanded activity has many outgoing control flows");
		}
		
		return target;
	}
}

