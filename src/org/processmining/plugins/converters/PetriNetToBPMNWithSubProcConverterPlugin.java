package org.processmining.plugins.converters;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.connections.ConnectionManager;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.AbstractDirectedGraphNode;
import org.processmining.models.graphbased.directed.ContainableDirectedGraphElement;
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
import org.processmining.models.graphbased.directed.bpmn.elements.SubProcess;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.plugins.graphalgorithms.SubprocessDiscovery;

/**
 * Conversion of a Petri net to BPMN model with Subprocesses corresponding to SESEs
 * (Single-Entry Single-Exit areas)
 * 
 * @author Anna Kalenkova 
 * Jun 27, 2015
 */
@Plugin(name = "Convert Petri net to BPMN diagram with Subprocesses", parameterLabels = { "Petri net" }, 
	returnLabels = {"BPMN Diagram"}, returnTypes = {BPMNDiagram.class}, userAccessible = true, help = "Converts Petri net to BPMN diagram with Subprocesses")

public class PetriNetToBPMNWithSubProcConverterPlugin {
	
	protected Map<String, Activity> conversionMap = null;
	protected Map<AbstractDirectedGraphNode, SubProcess> subprocesses = new HashMap<AbstractDirectedGraphNode, SubProcess>();
	
	@SuppressWarnings("unchecked")
	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Convert Petri net to BPMN with Subrocesses", requiredParameterLabels = { 0 })
	public BPMNDiagram convert(UIPluginContext context, PetrinetGraph petrinetGraph) {
		
		Progress progress = context.getProgress();
		progress.setCaption("Converting Data Petri net To BPMN diagram");
		BPMNDiagram bpmnDiagram = new BPMNDiagramImpl("");
	
		try {
			// Convert Petri net to a BPMN diagram
			bpmnDiagram = context.tryToFindOrConstructFirstNamedObject(BPMNDiagram.class, "Convert Petri net to BPMN diagram",
					BPMNConversionConnection.class, BPMNConversionConnection.BPMN_DIAGRAM,
					petrinetGraph);
			// Retrieve conversion map
			conversionMap = context.tryToFindOrConstructFirstNamedObject(Map.class, "Convert Petri net to BPMN diagram",
					BPMNConversionConnection.class, BPMNConversionConnection.CONVERSION_MAP,
					bpmnDiagram);
			
		} catch (ConnectionCannotBeObtained e) {
			context.log("Can't obtain connection for " + petrinetGraph.getLabel());
			e.printStackTrace();
		}
		
		constructBPMNDiagramWithSubprocesses(context, bpmnDiagram);
		progress.setCaption("Getting BPMN Visualization");
		
		// Add connection 
		ConnectionManager connectionManager = context.getConnectionManager();
		connectionManager.addConnection(new BPMNConversionConnection("Connection between "
				+ "BPMN model" + bpmnDiagram.getLabel()
				+ ", Petri net" + petrinetGraph.getLabel(),
				bpmnDiagram, petrinetGraph, conversionMap, true));
		
		return bpmnDiagram;
	}
	
	private BPMNDiagram constructBPMNDiagramWithSubprocesses(PluginContext context, BPMNDiagram bpmnDiagram) {

		BPMNNode startNode = retrieveStartNode(bpmnDiagram);
		BPMNNode endNode = retrieveEndNode(bpmnDiagram);

		SubprocessDiscovery subDisc = new SubprocessDiscovery(bpmnDiagram, startNode, endNode);
		Map<AbstractDirectedGraphNode, AbstractDirectedGraphNode> subProcBorders = subDisc.getSubProcessBorders();

		boolean dominatorsToConsider = true;
		while (dominatorsToConsider) {
			dominatorsToConsider = false;
			Set<AbstractDirectedGraphNode> dominatorsToRemove = new HashSet<AbstractDirectedGraphNode>();
			for (AbstractDirectedGraphNode dominator : subProcBorders.keySet()) {
				if ((dominator instanceof Activity) || 
					(dominator instanceof Gateway) && ((Gateway)dominator).getGatewayType().equals(GatewayType.DATABASED)
					|| bpmnDiagram.getInEdges(dominator).size() <= 1) {
					AbstractDirectedGraphNode postDominator = subProcBorders.get(dominator);
					if ((postDominator instanceof Activity) || 
							(postDominator instanceof Gateway) && ((Gateway)postDominator).getGatewayType().equals(GatewayType.PARALLEL)
							|| bpmnDiagram.getOutEdges(postDominator).size() <= 1)  {
						Set<AbstractDirectedGraphNode> parentDominators = subDisc.getDominators().get(dominator);
						parentDominators.remove(dominator);
						if (!Collections.disjoint(parentDominators, subProcBorders.keySet())) {
							dominatorsToConsider = true;
							continue;
						}
						AbstractDirectedGraphNode immediateDominator = subDisc.determineMinimalDominator(dominator, subprocesses.keySet(), false);
						SubProcess parentSubProc = subprocesses.get(immediateDominator);
						if (!dominator.equals(postDominator)) {
							SubProcess subProc = constructSubProcess(bpmnDiagram, dominator, postDominator, subDisc.getTreeOfDominators()
									.get(dominator), parentSubProc);
							subprocesses.put(dominator, subProc);
						}
					}
				}
				dominatorsToRemove.add(dominator);
			}
			for(AbstractDirectedGraphNode donimatorToRemove : dominatorsToRemove) {
				subProcBorders.remove(donimatorToRemove);
			}
		}
		return bpmnDiagram;
	}

	private SubProcess constructSubProcess(BPMNDiagram bpmnDiagram, AbstractDirectedGraphNode dominator,
			AbstractDirectedGraphNode postdominator, Set<AbstractDirectedGraphNode> childNodes, SubProcess parentSubProc) {

		SubProcess subProc = null;
		if(parentSubProc == null) {
			subProc = bpmnDiagram.addSubProcess("", false, false, false, false, false);
		} else {
			subProc = bpmnDiagram.addSubProcess("", false, false, false, false, false, parentSubProc);
		}
		subProc.addChild((ContainableDirectedGraphElement) dominator);
		((BPMNNode) dominator).setParentSubprocess(subProc);
		subProc.addChild((ContainableDirectedGraphElement) postdominator);
		((BPMNNode) postdominator).setParentSubprocess(subProc);
		for (AbstractDirectedGraphNode child : childNodes) {
			subProc.addChild((ContainableDirectedGraphElement) child);
			((BPMNNode) child).setParentSubprocess(subProc);
		}
				
		for(Flow flow : bpmnDiagram.getFlows()) {
			BPMNNode source = flow.getSource();
			BPMNNode target = flow.getTarget();
			if ((source.equals(dominator) || source.equals(postdominator) || childNodes.contains(source))
			&& (target.equals(dominator) || target.equals(postdominator) || childNodes.contains(target))) {
				subProc.addChild(flow);
				flow.setParent(subProc);
			}
		}
		
		for(BPMNEdge<? extends BPMNNode, ? extends BPMNNode> edge : bpmnDiagram.getInEdges(dominator)) {
			BPMNNode source = edge.getSource();
			bpmnDiagram.removeEdge(edge);
			bpmnDiagram.addFlow(source, subProc, "");
		}
		for(BPMNEdge<? extends BPMNNode, ? extends BPMNNode> edge : bpmnDiagram.getOutEdges(postdominator)) {
			BPMNNode target = edge.getTarget();
			bpmnDiagram.removeEdge(edge);
			bpmnDiagram.addFlow(subProc, target, "");
		}
		
		if(!(dominator instanceof Event)) {
			Event startEvent = bpmnDiagram.addEvent("", EventType.START, EventTrigger.NONE, EventUse.CATCH, subProc, true, null);
			bpmnDiagram.addFlow(startEvent, (BPMNNode)dominator, "");
		} else {
			Event startEvent = bpmnDiagram.addEvent("", EventType.START, EventTrigger.NONE, EventUse.CATCH, true, null);
			bpmnDiagram.addFlow(startEvent, subProc, "");
		}
		if(!(postdominator instanceof Event)) {
			Event endEvent = bpmnDiagram.addEvent("", EventType.END, EventTrigger.NONE, EventUse.THROW, subProc, true, null);
			bpmnDiagram.addFlow((BPMNNode)postdominator, endEvent, "");
		}
		else {
			Event endEvent = bpmnDiagram.addEvent("", EventType.END, EventTrigger.NONE, EventUse.THROW, true, null);
			bpmnDiagram.addFlow(subProc, endEvent, "");
		}
		
		return subProc;
	}

	private BPMNNode retrieveStartNode(BPMNDiagram bpmnDiagram) {
		for(BPMNNode node : bpmnDiagram.getEvents()) {
			if(node instanceof Event) {
				if (((Event)node).getEventType().equals(EventType.START)) {
					return node;
				}
			}
		}
		return null;
	}
	
	private BPMNNode retrieveEndNode(BPMNDiagram bpmnDiagram) {
		for(BPMNNode node : bpmnDiagram.getEvents()) {
			if(node instanceof Event) {
				if (((Event)node).getEventType().equals(EventType.END)) {
					return node;
				}
			}
		}
		return null;
	}
}
