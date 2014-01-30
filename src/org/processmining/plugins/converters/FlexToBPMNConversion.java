package org.processmining.plugins.converters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.connections.flexiblemodel.FlexEndTaskNodeConnection;
import org.processmining.models.connections.flexiblemodel.FlexStartTaskNodeConnection;
import org.processmining.models.flexiblemodel.EndTaskNodesSet;
import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.FlexEdge;
import org.processmining.models.flexiblemodel.FlexNode;
import org.processmining.models.flexiblemodel.SetFlex;
import org.processmining.models.flexiblemodel.StartTaskNodesSet;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramFactory;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventType;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventUse;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway.GatewayType;

@Plugin(name = "Convert C-Net to BPMN", parameterLabels = { "Causal Net" }, returnLabels = { "BPMN Diagram" }, returnTypes = { BPMNDiagram.class }, userAccessible = true, help = "Converts C-Net to BPMN")
public class FlexToBPMNConversion {
	
	// Map used during the conversion to identify source activity 
	// (skip all intermediate routers) for the current BPMN edge 
	private Map<BPMNEdge<BPMNNode, BPMNNode>, BPMNNode> mapOfSources
		= new HashMap<BPMNEdge<BPMNNode, BPMNNode>, BPMNNode>();

	//private static Map<String, Flow> flowMap = new HashMap<String, Flow>();
	@UITopiaVariant(affiliation = "HSE", author = "Anna Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "BPMN 2.0 Conversion", requiredParameterLabels = { 0 })
	public BPMNDiagram converter(UIPluginContext context, Flex model) {
		FlexSplitter splitter = new FlexSplitter();
		model = splitter.split(context, model);
		
		SetFlex startFlex = new SetFlex();
		startFlex.addAll(splitter.getStartNodes());
		SetFlex endFlex = new SetFlex();
		endFlex.addAll(splitter.getEndNodes());
		StartTaskNodesSet newStartSet = new StartTaskNodesSet();
		newStartSet.add(startFlex);
		EndTaskNodesSet newEndSet = new EndTaskNodesSet();
		newEndSet.add(endFlex);
		
		context.addConnection(new FlexStartTaskNodeConnection("Start tasks node of " + model.getLabel() + " connection", model, newStartSet));
		context.addConnection(new FlexEndTaskNodeConnection("End tasks node of " + model.getLabel() + " connection", model, newEndSet));
		
		return convert(context, model, newStartSet, newEndSet);
	}
	

	/**
	 * Conversion
	 * 
	 * @param context
	 * @param model
	 * @param startTaskNodesSet
	 * @param endTaskNodesSet
	 * @return
	 */
	public BPMNDiagram convert(PluginContext context, Flex model, StartTaskNodesSet startTaskNodesSet, EndTaskNodesSet endTaskNodesSet) {

		Progress progress = context.getProgress();

		/*
		 * Initialize the BPMN diagram.
		 */
		BPMNDiagram bpmn = BPMNDiagramFactory.newBPMNDiagram("BPMN Diagram");
		Map<String, BPMNNode> id2node = new HashMap<String, BPMNNode>();

		progress.setCaption("Converting To BPMN");
		progress.setIndeterminate(false);

		/*
		 * Convert C-Net to BPMN.
		 */
		convertAllActivities(bpmn, id2node, model/*, isFlexModelWithLifecycle(flex)*/);
		createStartEventAndConnectToStartActivity(model, bpmn, id2node, startTaskNodesSet);
		createEndEventAndConnectToEndActivity(model, bpmn, id2node, endTaskNodesSet);
		connectSequencesAndSplitGateways(bpmn, id2node, model);
		connectJoinGateways(bpmn, id2node, model);

		/*
		 * Set label for the BPMN diagram.
		 */
		progress.setCaption("Getting BPMN Visualization");
		context.getFutureResult(0).setLabel("Converted BPMN Model");
		return bpmn;
	}


	/**
	 *
	 * @param flex
	 * @param bpmn
	 * @param id2node
	 * @param flexEndTaskNodes
	 */
	private static void createEndEventAndConnectToEndActivity(Flex flex, BPMNDiagram bpmn,
			Map<String, BPMNNode> id2node, EndTaskNodesSet flexEndTaskNodes) {

		for (SetFlex nodes : flexEndTaskNodes) {

			for (FlexNode node : nodes) {

				Event endEvent = bpmn.addEvent("END EVENT", EventType.END, null, EventUse.THROW, null);
				
				BPMNNode connectTo = endEvent;
				BPMNNode fromNode = id2node.get("ID" + node.getId());
				if (fromNode == null)
					continue;
				bpmn.addFlow(fromNode, connectTo, null);
			}
		}
	}

	/**
	 * 
	 * @param flex
	 * @param bpmn
	 * @param id2node
	 * @param flexStartTaskNodes
	 */
	private static void createStartEventAndConnectToStartActivity(Flex flex, BPMNDiagram bpmn,
			Map<String, BPMNNode> id2node, StartTaskNodesSet flexStartTaskNodes) {

		for (SetFlex nodes : flexStartTaskNodes) {

			Event startEvent = bpmn.addEvent("START EVENT", EventType.START, null, EventUse.CATCH, null);

			BPMNNode connectTo = startEvent;
			
			if (nodes.size() > 1) {
				Gateway branchSplit = bpmn.addGateway("", GatewayType.DATABASED);
				bpmn.addFlow(startEvent, branchSplit, null);
				connectTo = branchSplit;
			}
			
			for (FlexNode node : nodes) {
				BPMNNode fromNode = id2node.get("ID" + node.getId());
				if (fromNode == null)
					continue;
				bpmn.addFlow(connectTo, fromNode, null);
			}
		}
	}

	/**
	 * 
	 * @param bpmn
	 * @param id2node
	 * @param flex
	 */
	private static void convertAllActivities(BPMNDiagram bpmn, Map<String, BPMNNode> id2node, Flex flex/*,
			boolean isFlexModelWithLifecycle*/) {
		for (FlexNode entry : flex.getNodes()) {
			String name = entry.getLabel();
			/*if (!isFlexModelWithLifecycle) {*/
				if (entry.getLabel().indexOf("+") != -1) {
					name = entry.getLabel().substring(0, entry.getLabel().indexOf("+"));

				}
			/*}*/
			Activity a = bpmn.addActivity(name, false, false, false, false, false);
			id2node.put("ID" + entry.getId(), a);
		}
	}

	/**
	 * 
	 * @param bpmn
	 * @param id2node
	 * @param flex
	 */
	private void connectSequencesAndSplitGateways(BPMNDiagram bpmn, Map<String, BPMNNode> id2node, Flex flex) {

		for (FlexNode entry : flex.getNodes()) {
			List<FlexEdge<? extends FlexNode, ? extends FlexNode>> outEdgesList = new ArrayList<FlexEdge<? extends FlexNode, ? extends FlexNode>>(
					flex.getOutEdges(entry));
			if (outEdgesList.size() < 1) {
				continue;
			} else if (outEdgesList.size() == 1) {
				FlexNode target = outEdgesList.get(0).getTarget();
				BPMNNode fromNode = id2node.get("ID" + entry.getId());
				BPMNNode toNode = id2node.get("ID" + target.getId());
				Flow sequenceFlow = bpmn.addFlow(fromNode, toNode, null);
				mapOfSources.put(sequenceFlow, id2node.get("ID" + entry.getId()));
			} else {
				fillSplitGatewaySettings(flex, entry, id2node, bpmn);
			}
		}
	}
	
	/**
	 * 
	 * @param bpmn
	 * @param id2node
	 * @param flex
	 */
	private void connectJoinGateways(BPMNDiagram bpmn, Map<String, BPMNNode> id2node, Flex flex) {

		for (FlexNode entry : flex.getNodes()) {
			fillJoinGatewaySettings(flex, entry, id2node, bpmn);
		}
	}

	/**
	 * 
	 * @param flex
	 * @param entry
	 * @param id2node
	 * @param bpmn
	 */
	private void fillSplitGatewaySettings(Flex flex, FlexNode entry,
			Map<String, BPMNNode> id2node, BPMNDiagram bpmn) {		
		BPMNNode firstNode = id2node.get("ID" + entry.getId());
		if (entry.getOutputNodes().size() > 1) {
			// CREATE GATEWAY ACTIVITY
			Gateway xorSplitGateway = bpmn.addGateway("", GatewayType.DATABASED);
			firstNode = xorSplitGateway;
			
			// CREATE NEW TRANSITIONS TO CONNECT ACTIVITIES TO GATEWAY
			BPMNNode fromNode = id2node.get("ID" + entry.getId());
			bpmn.addFlow(fromNode, xorSplitGateway, null);
		}
		Map<FlexNode, Gateway> joinGateways = new HashMap<FlexNode, Gateway>();
		for(SetFlex outputBinding : entry.getOutputNodes()) {
			BPMNNode outNode = firstNode;
			if(outputBinding.size() > 1) {
				// create AND-split
				Gateway parallelSplitGateway = bpmn.addGateway("", GatewayType.PARALLEL);				
				bpmn.addFlow(firstNode, parallelSplitGateway, "");
				outNode = parallelSplitGateway;
			}
			for(FlexNode node : outputBinding){
				BPMNNode inNode = id2node.get("ID" + node.getId());
				if(containsInManyOutBindings(node, entry)) {
					// create XOR-join
					if(joinGateways.get(node) == null) {
						Gateway xorJoinGateway = bpmn.addGateway("", GatewayType.DATABASED);
						bpmn.addFlow(xorJoinGateway,inNode, "");
						joinGateways.put(node, xorJoinGateway);
						inNode = xorJoinGateway;
					} else {
						inNode = joinGateways.get(node);
					}
				}
				Flow sequenceFlow = bpmn.addFlow(outNode, inNode, "");
				mapOfSources.put(sequenceFlow, id2node.get("ID" + entry.getId()));
			}
		}
	}
	
	/**
	 * 
	 * @param outNode
	 * @param node
	 * @return
	 */
	private boolean containsInManyOutBindings(FlexNode outNode, FlexNode node) {
		int numOfOutBindings = 0;
		for (SetFlex outputBinding : node.getOutputNodes()) {
			if (outputBinding.contains(outNode)) {
				numOfOutBindings++;
			}
		}
		return numOfOutBindings > 1;
	}

	/**
	 * 
	 * @param flex
	 * @param entry
	 * @param id2node
	 * @param bpmn
	 */
	private void fillJoinGatewaySettings(Flex flex, FlexNode entry, 
			Map<String, BPMNNode> id2node, BPMNDiagram bpmn) {
		Set<SetFlex> incomingBindings = entry.getInputNodes();
		BPMNNode lastNode = id2node.get("ID" + entry.getId());
		if(incomingBindings.size() > 1) {
			Gateway xorSplit = bpmn.addGateway("", GatewayType.DATABASED);
			bpmn.addFlow(xorSplit, lastNode, "");
			lastNode = xorSplit;
		}
		for(SetFlex incomingBinding : incomingBindings) {
			BPMNNode firstNode  = lastNode;
			if(incomingBinding.size() > 1) {
				Gateway andJoin = bpmn.addGateway("", GatewayType.PARALLEL);
				bpmn.addFlow(andJoin, lastNode, "");
				firstNode = andJoin;
			}
			for(FlexNode flexNode : incomingBinding) {
				BPMNEdge<?,?> edgeToDelete  = retrieveConnectingEdge(flexNode, entry, id2node, bpmn);
				if(edgeToDelete != null) {
					BPMNNode sourceNode = edgeToDelete.getSource();					
					bpmn.removeEdge(edgeToDelete);
					bpmn.addFlow(sourceNode, firstNode, "");
				}
			}
		}
	}
	
	/**
	 * 
	 * @param source
	 * @param target
	 * @param id2node
	 * @param bpmn
	 * @return
	 */
	private BPMNEdge<?,?> retrieveConnectingEdge(FlexNode source, FlexNode target, Map<String, BPMNNode> id2node,
			BPMNDiagram bpmn) {
		for (BPMNEdge<?,?> bpmnEdge : bpmn.getInEdges(id2node.get("ID" + target.getId()))) {
			BPMNNode node = mapOfSources.get(bpmnEdge);
			if (node != null && node.equals((id2node).get("ID" + source.getId()))) {
				return bpmnEdge;
			}
		}
		return null;
	}
}
