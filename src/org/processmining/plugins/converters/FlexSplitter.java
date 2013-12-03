package org.processmining.plugins.converters;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.FlexNode;
import org.processmining.models.flexiblemodel.SetFlex;

@Plugin(name = "CNetSplitter", parameterLabels = { "Causal Net" }, returnLabels = { "Causal Net" }, returnTypes = { Flex.class }, userAccessible = true, help = "Splits Flex model")
public class FlexSplitter {
	private Flex sourceModel;
	
	@UITopiaVariant(affiliation = "HSE", author = "Nikita Gundobin", email = "‎nikita.gundobin@gmail.com")
	@PluginVariant(variantLabel = "CNet splitting", requiredParameterLabels = { 0 })
	public Flex split(UIPluginContext context, Flex sourceModel) {
		this.sourceModel = sourceModel;	
		
		removeAllEmptyBindings();
		
		Set<FlexNode> leftNodes = new HashSet<FlexNode>(sourceModel.getNodes());
		FlexNode currentNode = getStartActivity(leftNodes);
		
		while(!leftNodes.isEmpty()) {
			Set<SetFlex> inputNodes = currentNode.getInputNodes();
			
			// nodes that don't need splitting
			if (inputNodes == null || inputNodes.size() <= 1 || (!intersects(inputNodes))) {
				leftNodes.remove(currentNode);
				currentNode = chooseNextCurrentNode(sourceModel.getNodes(), leftNodes);
				continue;
			}
			
			// splitting
			splitCurrentNode(currentNode);
			
			leftNodes.remove(currentNode);
			if (sourceModel.getNodes().contains(currentNode)) {
				removeNodeWithAllConnections(currentNode);
			}
			currentNode = chooseNextCurrentNode(sourceModel.getNodes(), leftNodes);
		}
		return sourceModel;
	}
	
	private void removeAllEmptyBindings() {
		for (FlexNode currentNode : sourceModel.getNodes()) {
			Set<SetFlex> inputBindings = currentNode.getInputNodes();
			removeEmptyBindings(inputBindings);
			Set<SetFlex> outBindings = currentNode.getOutputNodes();
			removeEmptyBindings(outBindings);
		}
	}

	private void removeNodeWithAllConnections(FlexNode node) {
		for (FlexNode currentNode : sourceModel.getNodes()) {
			Set<SetFlex> inputBindings = currentNode.getInputNodes();
			for (SetFlex inputBinding : inputBindings) {
				if(inputBinding.contains(node)) {
					inputBinding.remove(node);
				}
			}
			Set<SetFlex> outputBindings = currentNode.getOutputNodes();
			for (SetFlex outputBinding : outputBindings) {
				if(outputBinding.contains(node)) {
					outputBinding.remove(node);
				}
			}
		}
		sourceModel.removeNode(node);
	}
	
	public Set<FlexNode> getStartNodes() {
		Set<FlexNode> startNodes = new HashSet<FlexNode>();
		for (FlexNode node : sourceModel.getNodes()) {
			if (node.getInputNodes().size() == 0) {
				startNodes.add(node);
			}
		}
		return startNodes;
	}
	
	
	public Set<FlexNode> getEndNodes() {
		Set<FlexNode> endNodes = new HashSet<FlexNode>();
		for (FlexNode node : sourceModel.getNodes()) {
			if (node.getOutputNodes().size() == 0) {
				endNodes.add(node);
			}
		}
		return endNodes;
	}
		
	private void removeEmptyBindings(Set<SetFlex> bindings) {
		Set<SetFlex> bindingsToRemove = new HashSet<SetFlex>();
		for(SetFlex binding : bindings) {
			if(binding.size() == 0) {
				bindingsToRemove.add(binding);
			}
		}
		for(SetFlex binding : bindingsToRemove){
			bindings.remove(binding);
		}
	}

	
	private boolean intersects(Set<SetFlex> inputNodes) {		
		Set<FlexNode> allFlexNodes = new HashSet<FlexNode>();
		for(SetFlex setFlex : inputNodes) {
			for(FlexNode flexNode : setFlex) {
				if(allFlexNodes.contains(flexNode)) {
					return true;
				} else {
					allFlexNodes.add(flexNode);
				}
			}
		}
		return false;
	}

	private FlexNode getStartActivity(Iterable<FlexNode> nodes) {
		for (FlexNode node : nodes) {
			if (node.getInputNodes().isEmpty()) {
				return node;
			}
		}
		throw new IllegalArgumentException("Couldn't find node with no inputs");
	}

	private FlexNode chooseNextCurrentNode(Set<FlexNode> allNodes, Set<FlexNode> leftNodes) {
		for (FlexNode node : allNodes) {
			if (leftNodes.contains(node)) {
				return node;
			}
		}
		return null;
	}

	private void splitCurrentNode(FlexNode currentNode) {
		Set<FlexNode> nodeCopies = new HashSet<FlexNode>();
		int namingIndex = 0;
		
		// creating node copies and rebinding inputs
		for (SetFlex setFlex : currentNode.getInputNodes()) {
			FlexNode nodeCopy = sourceModel.addNode(currentNode.getLabel() + (namingIndex++));
			nodeCopy.addInputNodes(setFlex);		
			rebindOutputs(setFlex, currentNode, nodeCopy);
			nodeCopies.add(nodeCopy);
		}
		
		// creating terminal node and binding copies to it
		FlexNode terminalNode = sourceModel.addNode(currentNode.getLabel() + "_t");
		SetFlex outputForCopies = new SetFlex();
		outputForCopies.add(terminalNode);
		for (FlexNode node : nodeCopies) {
			node.addOutputNodes(outputForCopies);
			sourceModel.addArc(node, terminalNode);
			SetFlex inputForTerminal = new SetFlex();
			inputForTerminal.add(node);
			terminalNode.addInputNodes(inputForTerminal);
		}
		
		// rebinding outputs
		for (SetFlex setFlex : currentNode.getOutputNodes()) {
			terminalNode.addOutputNodes(setFlex);			
			rebindInputs(setFlex, currentNode, terminalNode);
		}
	}

	private void rebindOutputs(SetFlex setFlex, FlexNode currentNode, FlexNode nodeCopy) {
		Iterator<FlexNode> setFlexIterator = setFlex.iterator();
		while (setFlexIterator.hasNext()) {
			FlexNode node = setFlexIterator.next();
			Set<SetFlex> newOutputs = new HashSet<SetFlex>();
			for (SetFlex outputs : node.getOutputNodes()) {				
				if (outputs.contains(currentNode)) {
					SetFlex newSetFlex = new SetFlex();
					for(FlexNode flexNode : outputs) {
						if(!flexNode.equals(currentNode)) {
							newSetFlex.add(flexNode);
						}
					}
					newSetFlex.add(nodeCopy);
					newOutputs.add(newSetFlex);
					sourceModel.addArc(node, nodeCopy);
				}
			}
			for(SetFlex newSetFlex : newOutputs) {
				node.addOutputNodes(newSetFlex);
			}
		}
	}

	private void rebindInputs(SetFlex setFlex, FlexNode currentNode, FlexNode terminalNode) {
		Iterator<FlexNode> setFlexIterator = setFlex.iterator();
		while(setFlexIterator.hasNext()) {
			FlexNode node = setFlexIterator.next();
			for (SetFlex inputs : node.getInputNodes()) {
				if (inputs.contains(currentNode)) {
					inputs.remove(currentNode);
					inputs.add(terminalNode);
					sourceModel.addArc(terminalNode, node);
				}
			}
		}
	}
}