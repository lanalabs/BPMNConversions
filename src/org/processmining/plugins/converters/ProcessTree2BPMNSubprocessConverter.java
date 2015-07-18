package org.processmining.plugins.converters;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.NodeID;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.bpmn.elements.SubProcess;
import org.processmining.processtree.Block;
import org.processmining.processtree.Edge;
import org.processmining.processtree.Node;
import org.processmining.processtree.ProcessTree;
import org.processmining.processtree.impl.AbstractBlock;
import org.processmining.processtree.impl.AbstractEvent;
import org.processmining.processtree.impl.AbstractTask;
import org.processmining.processtree.impl.EdgeImpl;
import org.processmining.processtree.impl.ProcessTreeImpl;

import com.fluxicon.slickerbox.factory.SlickerFactory;

/**
 * Converts a process tree to BPMN model with subprocesses
 * 
 * @author Timur Badretdinov
 */
@Plugin(name = "Convert Process tree to BPMN diagram with subprocesses", parameterLabels = { "Process tree" }, 
returnLabels = { "BPMN Diagram", "Conversion map" }, returnTypes = { BPMNDiagram.class, Map.class }, 
userAccessible = true, help = "Converts Process tree to BPMN diagram with subprocesses")
public class ProcessTree2BPMNSubprocessConverter {
	ProcessTree2BPMNConverter pt2bpmn = new ProcessTree2BPMNConverter();

	private UIPluginContext _context;

	private ConversionParameters conversionParameters;

	private static final int BASE_LEVEL = 1;
	private int maxLevel;
	private Map<Node, Integer> levelMap;

	private Map<Node, Node> originalNodesMap;
	private Map<ProcessTree, Map<Node, ProcessTree>> processTreeMap;

	@UITopiaVariant(affiliation = "HSE", author = "T. Badretdinov", email = "trbadretdinov@edu.hse.ru")
	@PluginVariant(variantLabel = "Convert Process tree to BPMN with subprocesses and simplify", 
	requiredParameterLabels = { 0 })
	public Object[] convert(UIPluginContext context, ProcessTree tree) {
		return convertToBPMNSubprocess(context, tree);
	}

	private Object[] convertToBPMNSubprocess(UIPluginContext context, ProcessTree tree) {
		initialize(context);
		// Clone tree to freely modify it
		ProcessTree newTree = new ProcessTreeImpl(tree);
		maxLevel = levelTree(newTree);
		// Conversion dialog
		ConversionDialog dialog = new ConversionDialog(maxLevel);
		conversionParameters = dialog.parameters;
		InteractionResult result = context.showWizard("Convert process tree to BPMN diagram with subprocesses", true, true, dialog);
		if (result != InteractionResult.FINISHED) {
			return new Object[]{null, null};
		}
		// Set start node map
		for (Node node : tree.getNodes()) {
			originalNodesMap.put(node, node);
		}
		// Creates process trees based on given one
		Map<Node, ProcessTree> startMap = Collections.synchronizedMap(new HashMap<Node, ProcessTree>());
		simplifyTree(newTree, startMap);
		processTreeMap.put(newTree, startMap);
		// Creates BPMN diagram based on created process trees
		BPMNDiagram output = getBPMNWithSubprocess(newTree);
		Map<NodeID, UUID> idMap = getIdMap(tree);
		return new Object[] {output, idMap};
	}

	/**
	 * Initializes some global variables
	 * @param context
	 */
	private void initialize(UIPluginContext context) {
		_context = context;
		levelMap = new HashMap<Node, Integer>();
		originalNodesMap = new HashMap<Node, Node>();
		processTreeMap = new HashMap<ProcessTree, Map<Node,ProcessTree>>();
	}

	/**
	 * Set level to the each node of a tree.
	 * 
	 * @param tree
	 * @return maximum level of node (length of tree)
	 */
	private int levelTree(ProcessTree tree) {
		return levelNode(tree.getRoot());
	}

	/**
	 * Set level to the given node.
	 * 
	 * @param node
	 * @return level of given node
	 */
	private int levelNode(Node node) {
		List<Node> childrenList = getChildren(node);
		if (childrenList == null || childrenList.isEmpty()) {
			levelMap.put(node, BASE_LEVEL);
			return BASE_LEVEL;
		} else {
			int maxChildLevel = BASE_LEVEL;
			for (Node child : childrenList) {
				int childLevel = levelNode(child);
				maxChildLevel = Math.max(maxChildLevel, childLevel);
			}
			int parentLevel = maxChildLevel + 1;
			levelMap.put(node, parentLevel);
			return parentLevel;
		}
	}

	/**
	 * Simplifies tree by simplifying each child of the root.
	 * 
	 * @param tree
	 * @param endNodeMap
	 */
	private void simplifyTree(ProcessTree tree, Map<Node, ProcessTree> endNodeMap) {
		List<Node> childrenList = getChildren(tree.getRoot());
		if (childrenList != null) {
			for (Node child : childrenList) {
				simplifyNode(child, endNodeMap);
			}
		}
	}

	/**
	 * Simplifies given node.
	 * If node is marked, makes new tree, simplifies it and adds this tree to the tree map,
	 * otherwise simplifies each child of given node.
	 * 
	 * @param node
	 * @param endNodeMap map that contains every end node of current tree
	 */
	private void simplifyNode(Node node, Map<Node, ProcessTree> endNodeMap) {
		List<Node> childrenList = getChildren(node);
		if (isMarked(originalNodesMap.get(node))) {
			// Create subtree and simplify it
			ProcessTree subTree = createProcessTreeFromNode(node);
			Map<Node, ProcessTree> subEndNodeMap = Collections.synchronizedMap(new HashMap<Node, ProcessTree>());
			simplifyTree(subTree, subEndNodeMap);
			processTreeMap.put(subTree, subEndNodeMap);
			// Create end node
			Node newNode = new AbstractTask.Automatic("End");
			node.getProcessTree().addNode(newNode);
			updateEdges(node, newNode);
			endNodeMap.put(newNode, subTree);
			// Remove old node with its children
			removeNodeAndItsChildren(node);
		} else {
			updateNodeInParentList(node);
			if (childrenList != null) {
				for (Node child : childrenList) {
					simplifyNode(child, endNodeMap);
				}
			}
		}
	}

	/**
	 * Checks whether node is marked or not. Each marked node will be converted to diagram.
	 * 
	 * @param node
	 * @return true if node marked, false otherwise
	 */
	private boolean isMarked(Node node) {
		int level = levelMap.get(node);
		if (level == BASE_LEVEL || level == maxLevel) {
			return false;
		}
		if (conversionParameters.conversionMethod == ConvertionMethods.FOLD_BOTTOM_MOST_LEVELS.ordinal()) {
			return level <= (conversionParameters.foldingLevel + BASE_LEVEL);
		}
		// Fold levels all over the tree
		if (conversionParameters.foldingLevel == 1) {
			return (level == maxLevel / 2);
		} else {
			for (int i = 0; i < conversionParameters.foldingLevel ; i++) {
				if (2 + i * (maxLevel - 3)/(conversionParameters.foldingLevel - 1) == level) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Creates new process tree where given node is root. All children of the node is added too.
	 * 
	 * @param node
	 * @return new process tree
	 */
	private ProcessTree createProcessTreeFromNode(Node node) {
		ProcessTree processTree = new ProcessTreeImpl();
		Node root = addNodeToProcessTree(processTree, node);
		processTree.setRoot(root);
		return processTree;
	}

	/**
	 * Adds given node and all its children.
	 * Method clones each of the nodes instead of using existing instances.
	 * 
	 * @param processTree
	 * @param node
	 * @return added node
	 */
	private Node addNodeToProcessTree(ProcessTree processTree, Node node) {
		Node newNode = generateNewNode(node);
		processTree.addNode(newNode);
		// Add node children
		List<Node> childrenList = getChildren(node);
		if (childrenList != null) {
			for (Node childNode : childrenList) {
				Node newChildNode = addNodeToProcessTree(processTree, childNode);
				newChildNode.addParent((Block)newNode);
			}
		}
		// Update original nodes map
		Node originalNode = originalNodesMap.get(node);
		originalNodesMap.remove(node);
		originalNodesMap.put(newNode, originalNode);
		return newNode;
	}

	/**
	 * Clones given node based on its type.
	 * 
	 * @param node
	 * @return cloned node
	 */
	private Node generateNewNode(Node node) {
		ProcessTree tree = node.getProcessTree();
		String nodeName = node.getName();
		switch (tree.getType(node)) {
			case XOR: {
				return new AbstractBlock.Xor(nodeName);
			}
			case OR: {
				return new AbstractBlock.Or(nodeName);
			}
			case AND: {
				return new AbstractBlock.And(nodeName);
			}
			case SEQ: {
				return new AbstractBlock.Seq(nodeName);
			}
			case DEF: {
				return new AbstractBlock.Def(nodeName);
			}
			case LOOPXOR: {
				return new AbstractBlock.XorLoop(nodeName);
			}
			case LOOPDEF: {
				return new AbstractBlock.DefLoop(nodeName);
			}
			case PLACEHOLDER: {
				return new AbstractBlock.PlaceHolder(nodeName);
			}
			case AUTOTASK :
			case MANTASK : {
				return new AbstractTask.Automatic(node.getName());
			}
			case MESSAGE : {
				return new AbstractEvent.Message(nodeName, ((AbstractEvent)node).getMessage());
			}
			case TIMEOUT : {
				return new AbstractEvent.TimeOut(nodeName, ((AbstractEvent)node).getMessage());
			}
			default :
				break;
		}
		return null;
	}

	/**
	 * Converts given process tree to BPMN diagram.
	 * Uses process tree map to build diagrams of each process tree related to end nodes of the tree.
	 * Adds these diagrams to created subprocesses. Updates flows and removes end nodes.
	 * 
	 * @param tree
	 * @return BPMN diagram with subprocess
	 */
	@SuppressWarnings("unchecked")
	private BPMNDiagram getBPMNWithSubprocess(ProcessTree tree) {
		System.out.println(processTreeMap.get(tree).entrySet());
		// Convert main tree to diagram
		Object[] bpmnWithMap = pt2bpmn.convert(_context, tree, conversionParameters.simplify);
		BPMNDiagram diagram = (BPMNDiagram) bpmnWithMap[0];
		Map<NodeID, UUID> idMap = (Map<NodeID, UUID>) bpmnWithMap[1];
		// Process every end node
		for (Map.Entry<Node, ProcessTree> entry : processTreeMap.get(tree).entrySet()) {
			// Find BPMN node related to process tree end node
			Node endNode = entry.getKey();
			NodeID endBPMNNodeId = getFirstKeyByValue(idMap, endNode.getID());
			BPMNNode endBPMNNode = getBPMNNodeByNodeID(diagram, endBPMNNodeId);
			// Add subprocess related to the end node
			SubProcess subProcess = diagram.addSubProcess("", false, false, false, false, false);
			// Generate diagram based on process tree related to the end node
			BPMNDiagram subDiagram = getBPMNWithSubprocess(entry.getValue());
			setChildToSubprocess(diagram, subDiagram, subProcess);
			// Remove old flows and add new ones
			updateFlows(diagram, endBPMNNode, subProcess);
			// Remove end node from diagram
			diagram.removeNode(endBPMNNode);
		}
		return diagram;
	}

	/**
	 * Finds first such key which value is equal to given one.
	 * 
	 * @param map
	 * @param value
	 * @return map key
	 */
	private <T, U> T getFirstKeyByValue(Map<T,U> map, U value) {
		for (Map.Entry<T, U> entry : map.entrySet()) {
			if (entry.getValue() == value) {
				return entry.getKey();
			}
		}
		return null;
	}

	/**
	 * Finds BPMN node which node ID is equal to given one.
	 * 
	 * @param bpmnDiagram
	 * @param nodeID
	 * @return BPMN node
	 */
	private BPMNNode getBPMNNodeByNodeID(BPMNDiagram bpmnDiagram, NodeID nodeID) {
		for (BPMNNode bpmnNode : bpmnDiagram.getNodes()) {
			if (bpmnNode.getId() == nodeID) {
				return bpmnNode;
			}
		}
		return null;
	}

	/**
	 * Sets given subdiagram as a subprocess of diagram.
	 * 
	 * @param diagram
	 * @param subDiagram
	 * @param subProcess
	 */
	private void setChildToSubprocess(BPMNDiagram diagram, BPMNDiagram subDiagram, SubProcess subProcess) {
		Map<BPMNNode, BPMNNode> map = addNodes(diagram, subDiagram, subProcess);
		setHierarchy(map);
		addFlows(diagram, subDiagram, map);
	}

	/**
	 * Adds every node of given subdiagram to another diagram. Makes nodes child of subprocess.
	 * 
	 * @param diagram
	 * @param subDiagram
	 * @param subProcess
	 * @return map of every old node related to created one.
	 */
	private Map<BPMNNode, BPMNNode> addNodes(BPMNDiagram diagram, BPMNDiagram subDiagram, SubProcess subProcess) {
		Map<BPMNNode, BPMNNode> map = new HashMap<BPMNNode, BPMNNode>();
		for (BPMNNode node : subDiagram.getNodes()) {
			BPMNNode newNode = addNode(diagram, node);
			newNode.setParentSubprocess(subProcess);
			map.put(node, newNode);
		}
		return map;
	}

	/**
	 * Adds node to the diagram based on type of given node.
	 * 
	 * @param diagram
	 * @param node
	 * @return created node
	 */
	private BPMNNode addNode(BPMNDiagram diagram, BPMNNode node) {
		if (node instanceof SubProcess) {
			return diagram.addSubProcess(node.getLabel(), false, false, false, false, false);
		}
		if (node instanceof Activity) {
			return diagram.addActivity(node.getLabel(), false, false, false, false, false);
		}
		if (node instanceof Gateway) {
			return diagram.addGateway(node.getLabel(), ((Gateway)node).getGatewayType());
		} 
		if (node instanceof Event) {
			return diagram.addEvent(node.getLabel(), ((Event)node).getEventType(), ((Event)node).getEventTrigger(), ((Event)node).getEventUse(), true, null);
		}
		return null;
	}

	/**
	 * Sets every new node to the subprocess of old one.
	 * 
	 * @param map
	 */
	private void setHierarchy(Map<BPMNNode, BPMNNode> map) {
		for (Map.Entry<BPMNNode, BPMNNode> entry : map.entrySet()) {
			SubProcess subProcess = (SubProcess) map.get(entry.getKey().getParentSubProcess());
			if (subProcess != null) {
				entry.getValue().setParentSubprocess(subProcess);
			}
		}
	}

	/**
	 * Creates flows based on ones on subdiagram.
	 * 
	 * @param diagram
	 * @param subDiagram
	 * @param map
	 */
	private void addFlows(BPMNDiagram diagram, BPMNDiagram subDiagram, Map<BPMNNode, BPMNNode> map) {
		for (Flow flow : subDiagram.getFlows()) {
			BPMNNode source = map.get(flow.getSource());
			BPMNNode target = map.get(flow.getTarget());
			if (source != null && target != null) {
				diagram.addFlow(source, target, "");
			}
		}
	}

	/**
	 * Updates edges to make them relate to the new nodes instead of old ones.
	 * 
	 * @param oldNode
	 * @param newNode
	 */
	private void updateEdges(Node oldNode, Node newNode) {
		ProcessTree tree = oldNode.getProcessTree();
		Collection<Edge> oldEdges = oldNode.getIncomingEdges();
		Collection<Edge> newEdges = getNewEdges(oldNode, newNode, oldEdges);
		removeOldEdges(tree, oldEdges);
		// Add new edges
		for (Edge edge : newEdges) {
			tree.addEdge(edge);
		}
	}

	/**
	 * Creates new edges for each old one.
	 * 
	 * @param oldNode
	 * @param newNode
	 * @param oldEdges
	 * @return set of new edges
	 */
	private Collection<Edge> getNewEdges(Node oldNode, Node newNode, Collection<Edge> oldEdges) {
		List<Edge> newEdges = new ArrayList<Edge>();
		// Add only edge where node is target
		for (Edge edge : oldEdges) {
			Edge newFlow = new EdgeImpl(edge.getSource(), newNode);
			newEdges.add(newFlow);
		}
		return newEdges;
	}

	/**
	 * Removes given set of edges form the process tree.
	 * 
	 * @param tree
	 * @param edges
	 */
	private void removeOldEdges(ProcessTree tree, Collection<Edge> edges) {
		for (Edge edge : edges) {
			tree.removeEdge(edge);
			edge.getSource().removeOutgoingEdge(edge);
		}
	}

	/**
	 * Removes given node and its children from the process tree. Removes all related edges.
	 * 
	 * @param node
	 */
	private void removeNodeAndItsChildren(Node node) {
		ProcessTree tree = node.getProcessTree();
		Collection<Node> children = getChildren(node);
		if (children != null) {
			for (Node child : children) {
				removeNodeAndItsChildren(child);
			}
		}
		// Remove old node
		tree.removeNode(node);
		// Remove old edges (if any)
		removeOldEdges(tree, node.getIncomingEdges());
	}

	/**
	 * Updates flows to make them relate to the new nodes instead of old ones.
	 * 
	 * @param bpmnDiagram
	 * @param oldNode
	 * @param newNode
	 */
	private void updateFlows(BPMNDiagram bpmnDiagram, BPMNNode oldNode, BPMNNode newNode) {
		Collection<Flow> flows = bpmnDiagram.getFlows();
		Collection<Flow> oldFlows = getOldFlows(oldNode, flows);
		Collection<Flow> newFlows = getNewFlows(oldNode, newNode, oldFlows);
		// Remove old flows
		for (Flow flow : oldFlows) {
			bpmnDiagram.removeEdge(flow);
		}
		// Add new flows
		for (Flow flow : newFlows) {
			bpmnDiagram.addFlow(flow.getSource(), flow.getTarget(), flow.getLabel());
		}
	}

	/**
	 * Finds all flows that relates to the node.
	 * 
	 * @param node
	 * @param flows
	 * @return
	 */
	private Collection<Flow> getOldFlows(BPMNNode node, Collection<Flow> flows) {
		List<Flow> oldFlows = new ArrayList<Flow>();
		for (Flow flow : flows) {
			if (flow.getSource() == node) {
				oldFlows.add(flow);
			} else if (flow.getTarget() == node) {
				oldFlows.add(flow);
			}
		}
		return oldFlows;
	}

	/**
	 * Creates new flow for each edge that relates to the old node.
	 * 
	 * @param oldNode
	 * @param newNode
	 * @param flows set of all flows to search through
	 * @return set of created flows
	 */
	private Collection<Flow> getNewFlows(BPMNNode oldNode, BPMNNode newNode, Collection<Flow> flows) {
		List<Flow> newFlows = new ArrayList<Flow>();
		for (Flow flow : flows) {
			if (flow.getSource() == oldNode) {
				Flow newFlow = new Flow(newNode, flow.getTarget(), flow.getLabel());
				newFlows.add(newFlow);
			} else if (flow.getTarget() == oldNode) {
				Flow newFlow = new Flow(flow.getSource(), newNode, flow.getLabel());
				newFlows.add(newFlow);
			}
		}
		return newFlows;
	}

	/**
	 * Gets children if given node is block. Returns null otherwise.
	 * 
	 * @param node
	 * @return list of children
	 */
	private List<Node> getChildren(Node node) {
		return (node instanceof Block)
				? ((Block)node).getChildren()
				: null;
	}
	
	/**
	 * Removes corresponding edge from outgoing edge list and then adds it again to the end.
	 * It helps keep order of tree's nodes as they were.
	 * 
	 * @param node
	 */
	private void updateNodeInParentList(Node node) {
		Block parent = node.getParents().iterator().next();
		Edge nodeEdge = node.getIncomingEdges().iterator().next();
		parent.removeOutgoingEdge(nodeEdge);
		parent.addOutgoingEdge(nodeEdge);
	}
	
	/**
	 * Creates id map based on outdated id map and nodes map.
	 * 
	 * @param tree
	 * @return new id map
	 */
	@SuppressWarnings("unchecked")
	private Map<NodeID, UUID> getIdMap(ProcessTree tree) {
		Map<NodeID, UUID> idMap = (Map<NodeID, UUID>) pt2bpmn.convert(_context, tree)[1];
		Map<NodeID, UUID> newIdMap = new HashMap<NodeID, UUID>();
		for (Map.Entry<Node, Node> entry : originalNodesMap.entrySet()) {
			newIdMap.put(getFirstKeyByValue(idMap, entry.getValue().getID()), entry.getKey().getID());
		}
		return newIdMap;
	}

	/**
	 * Represents conversion parameters
	 */
	private class ConversionParameters {
		private int conversionMethod = 0;
		private int foldingLevel = 1;
		private boolean simplify = true;

		public ConversionParameters() {
		}
	}

	enum ConvertionMethods
	{
		FOLD_LEVELS_EVENLY,
		FOLD_BOTTOM_MOST_LEVELS
	}

	/**
	 * Represents conversion parameters dialog
	 */
	@SuppressWarnings("serial")
	private class ConversionDialog extends JPanel {
		private final String[] convertionMethods = new String[]{"Fold levels evenly", "Fold levels starting from bottom"};

		public ConversionParameters parameters = new ConversionParameters();

		public ConversionDialog(int treeLevel) {
			SlickerFactory factory = SlickerFactory.instance();

			int gridy = 0;

			setLayout(new GridBagLayout());

			// Conversion method
			final JLabel convertionMethodLabel = factory.createLabel("Convertion method");
			{
				GridBagConstraints cConvertionMethodLabel = new GridBagConstraints();
				cConvertionMethodLabel.gridx = 0;
				cConvertionMethodLabel.gridy = gridy;
				cConvertionMethodLabel.weightx = 0.2;
				cConvertionMethodLabel.anchor = GridBagConstraints.NORTHWEST;
				add(convertionMethodLabel, cConvertionMethodLabel);
			}

			final JComboBox<?> conversionMethodsCombobox = factory.createComboBox(convertionMethods);
			{
				conversionMethodsCombobox.setSelectedIndex(parameters.conversionMethod);
				GridBagConstraints cConversionMethodCombobox = new GridBagConstraints();
				cConversionMethodCombobox.gridx = 1;
				cConversionMethodCombobox.gridy = gridy;
				cConversionMethodCombobox.weightx = 0.6;
				cConversionMethodCombobox.anchor = GridBagConstraints.NORTHWEST;
				cConversionMethodCombobox.fill = GridBagConstraints.HORIZONTAL;
				add(conversionMethodsCombobox, cConversionMethodCombobox);
			}

			gridy++;

			// Spacer
			{
				JLabel spacer = factory.createLabel(" ");
				GridBagConstraints cSpacer = new GridBagConstraints();
				cSpacer.gridx = 0;
				cSpacer.gridy = gridy;
				cSpacer.anchor = GridBagConstraints.WEST;
				add(spacer, cSpacer);
			}

			gridy++;

			// Folding level
			final JLabel foldingLevelLabel = factory.createLabel("Folding level");
			{
				GridBagConstraints cFoldingLevelLabel = new GridBagConstraints();
				cFoldingLevelLabel.gridx = 0;
				cFoldingLevelLabel.gridy = gridy;
				cFoldingLevelLabel.weightx = 0.2;
				cFoldingLevelLabel.anchor = GridBagConstraints.WEST;
				add(foldingLevelLabel, cFoldingLevelLabel);
			}

			final JSlider foldingLevelSlider = factory.createSlider(SwingConstants.HORIZONTAL);
			{
				foldingLevelSlider.setMinimum(1);
				foldingLevelSlider.setMaximum(treeLevel - 2);
				foldingLevelSlider.setValue(parameters.foldingLevel);
				GridBagConstraints cFoldingLevelSlider = new GridBagConstraints();
				cFoldingLevelSlider.gridx = 1;
				cFoldingLevelSlider.gridy = gridy;
				cFoldingLevelSlider.fill = GridBagConstraints.HORIZONTAL;
				add(foldingLevelSlider, cFoldingLevelSlider);
			}

			final JLabel foldingLevelValue = factory.createLabel("1");
			{
				GridBagConstraints cFoldingLevelValue = new GridBagConstraints();
				cFoldingLevelValue.gridx = 2;
				cFoldingLevelValue.gridy = gridy;
				cFoldingLevelValue.weightx = 0.1;
				add(foldingLevelValue, cFoldingLevelValue);
			}

			gridy++;

			// Spacer
			{
				JLabel spacer = factory.createLabel(" ");
				GridBagConstraints cSpacer = new GridBagConstraints();
				cSpacer.gridx = 0;
				cSpacer.gridy = gridy;
				cSpacer.anchor = GridBagConstraints.WEST;
				add(spacer, cSpacer);
			}

			gridy++;

			// Simplify BPMN
			final JLabel simplifyLabel = factory.createLabel("Simplify BPMN");
			{
				GridBagConstraints cSimplifyLabel = new GridBagConstraints();
				cSimplifyLabel.gridx = 0;
				cSimplifyLabel.gridy = gridy;
				cSimplifyLabel.weightx = 0.2;
				cSimplifyLabel.anchor = GridBagConstraints.WEST;
				add(simplifyLabel, cSimplifyLabel);
			}

			final JCheckBox simplifyCheckBox = factory.createCheckBox("", parameters.simplify);
			{
				GridBagConstraints cSimplifyCheckBox = new GridBagConstraints();
				cSimplifyCheckBox.gridx = 1;
				cSimplifyCheckBox.gridy = gridy;
				cSimplifyCheckBox.weightx = 1;
				cSimplifyCheckBox.anchor = GridBagConstraints.WEST;
				add(simplifyCheckBox, cSimplifyCheckBox);
			}

			// Set listeners
			conversionMethodsCombobox.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					parameters.conversionMethod = conversionMethodsCombobox.getSelectedIndex();
				}
			});

			foldingLevelSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent arg0) {
					Integer value = foldingLevelSlider.getValue();
					parameters.foldingLevel = value;
					foldingLevelValue.setText(value.toString());
				}
			});

			simplifyCheckBox.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent arg0) {
					parameters.simplify = simplifyCheckBox.isSelected();
				}
			});
		}
	}
}
