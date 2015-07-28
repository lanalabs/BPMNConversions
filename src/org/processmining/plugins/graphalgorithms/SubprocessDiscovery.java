package org.processmining.plugins.graphalgorithms;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.processmining.models.graphbased.directed.AbstractDirectedGraphEdge;
import org.processmining.models.graphbased.directed.AbstractDirectedGraphNode;
import org.processmining.models.graphbased.directed.ContainableDirectedGraphElement;
import org.processmining.models.graphbased.directed.ContainingDirectedGraphNode;
import org.processmining.models.graphbased.directed.DirectedGraph;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.DataAssociation;
import org.processmining.models.graphbased.directed.bpmn.elements.Swimlane;

/**
 * Discovering dominators, post-dominators and enclosing subprocess for graph nodes
 * For the algorithm description see {@link  http://en.wikipedia.org/wiki/Dominator_(graph_theory)}
 *
 * @author Anna Kalenkova
 * Aug 2, 2013
 */
public class SubprocessDiscovery {

	/**
	 * The entire graph
	 */
	private DirectedGraph<? extends AbstractDirectedGraphNode, 
			? extends AbstractDirectedGraphEdge<?,?>> graph;
	
	/**
	 * Parent element
	 */
	ContainingDirectedGraphNode parentElement;
	
	/**
	 * Start node of the graph
	 */
	private AbstractDirectedGraphNode startNode;
	
	/**
	 * End node of the graph
	 */
	private AbstractDirectedGraphNode endNode;
	
	/**
	 * Dominators for all diagram nodes
	 */
	private Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> dominators;
	
	public Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> getDominators() {
		return dominators;
	}

	/**
	 * Post-dominators for all diagram nodes
	 */
	private Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> postDominators;
	
	public Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> getPostDominators() {
		return postDominators;
	}

	/**
	 * Tree of dominators (specifies children for each node)
	 */
	private Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> treeOfDominators;
	
	private Map<AbstractDirectedGraphNode, AbstractDirectedGraphNode> subProcessBorders;
	
	public Map<AbstractDirectedGraphNode, AbstractDirectedGraphNode> getSubProcessBorders() {
		return subProcessBorders;
	}

	public Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> getTreeOfDominators() {
		return treeOfDominators;
	}

	public Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> getTreeOfPostDominators() {
		return treeOfPostDominators;
	}

	/**
	 * Tree of post-dominators (specifies children for each node)
	 */
	private Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> treeOfPostDominators;
	
	public SubprocessDiscovery(DirectedGraph<? extends AbstractDirectedGraphNode, 
			? extends AbstractDirectedGraphEdge<?,?>> directedGraph, AbstractDirectedGraphNode startNode,
			AbstractDirectedGraphNode endNode, ContainingDirectedGraphNode parentElement) {
		
		System.out.println("Parent element " + parentElement);
		this.parentElement = parentElement;
		this.graph = directedGraph;
		this.startNode = startNode;
		this.endNode = endNode;
		this.dominators = determineDominatorsForGraphNodes(false);
		this.postDominators = determineDominatorsForGraphNodes(true);
		this.treeOfDominators = constructTree(dominators);
		this.treeOfPostDominators = constructTree(postDominators);
		this.subProcessBorders = constrctSubProcBorders();

	}
	
	public SubprocessDiscovery(DirectedGraph<? extends AbstractDirectedGraphNode, 
			? extends AbstractDirectedGraphEdge<?,?>> directedGraph, AbstractDirectedGraphNode startNode,
			AbstractDirectedGraphNode endNode) {
		
		this(directedGraph, startNode, endNode, null);
	}
	
	private Map<AbstractDirectedGraphNode, AbstractDirectedGraphNode> constrctSubProcBorders() {
		subProcessBorders = new HashMap<AbstractDirectedGraphNode, AbstractDirectedGraphNode>(); 
		for(AbstractDirectedGraphNode dominator : treeOfDominators.keySet()) {
			for(AbstractDirectedGraphNode postDominator : treeOfPostDominators.keySet()) {
				
				Set<AbstractDirectedGraphNode> dominatorsChildren = treeOfDominators.get(dominator);
				dominatorsChildren.add(dominator);
				Set<AbstractDirectedGraphNode> postDominatorsChildren = treeOfPostDominators.get(postDominator);
				postDominatorsChildren.add(postDominator);
				
				if(dominatorsChildren.containsAll(postDominatorsChildren)
						&& postDominatorsChildren.containsAll(dominatorsChildren)) {
							subProcessBorders.put(dominator, postDominator);
				}
			}
		}
		return subProcessBorders;
	}

	/**
	 * Determine a minimal dominator
	 *
	 * @param node
	 * @param inversive - true for determining post-dominators
	 * @return
	 */
	public AbstractDirectedGraphNode determineMinimalDominator
	(AbstractDirectedGraphNode node, Set<AbstractDirectedGraphNode> concidredDominators, boolean inversive) {

		Set<AbstractDirectedGraphNode> actualDominators = new HashSet<AbstractDirectedGraphNode>();
		actualDominators.addAll(dominators.get(node));
		actualDominators.retainAll(concidredDominators);
		
		AbstractDirectedGraphNode immediateDominator = null;
		// Determine the immediate dominator 
		if (actualDominators.size() > 0) {
			 immediateDominator = actualDominators.iterator().next();
		}
		for(AbstractDirectedGraphNode dominator : actualDominators) {
			if((inversive ? postDominators : dominators).get(dominator).contains(immediateDominator)) {
				immediateDominator = dominator;
			}
		}
		
		return immediateDominator;
	}
	
	
	/**
	 * Determine minimal common dominator
	 *
	 * @param innerNodes
	 * @param inversive - true for determining post-dominators
	 * @return
	 */
	public AbstractDirectedGraphNode determineImmediateCommonDominator
	(List<AbstractDirectedGraphNode> innerNodes, boolean inversive) {

		// First step: set of common dominators contains dominators of any node
		List<AbstractDirectedGraphNode> commonDominators = new ArrayList<AbstractDirectedGraphNode>();
		commonDominators.addAll((inversive ? postDominators : dominators).get(innerNodes.get(0)));
		Set<AbstractDirectedGraphNode> dominatorsToRemove = new HashSet<AbstractDirectedGraphNode>();
		
		// Calculate the intersection of dominators
		for(AbstractDirectedGraphNode innerNode : innerNodes) {
			for(AbstractDirectedGraphNode dominator : commonDominators) {
				if(!(inversive ? postDominators : dominators).get(innerNode).contains(dominator)) {
					dominatorsToRemove.add(dominator);
				}
			}
		}
		
		// Remone not common dominators
		commonDominators.removeAll(dominatorsToRemove);
		
		// Determine the minimal dominator for the set of node
		AbstractDirectedGraphNode minimalDominator = commonDominators.get(0);
		for(AbstractDirectedGraphNode dominator : commonDominators) {
			if (((inversive ? postDominators : dominators).get(dominator).contains(minimalDominator))) {
				minimalDominator = dominator;
			}
		}
		
		return minimalDominator;
	}
	
	
	public Set<ContainableDirectedGraphElement> determineSubprocessElements(
			AbstractDirectedGraphNode subprocessStartNode, 
			AbstractDirectedGraphNode subprocessEndNode) {
		
		Set<ContainableDirectedGraphElement> subprocessNodes
			= DFSForSubprocessDiscovery(subprocessStartNode, subprocessEndNode, 
					new HashSet<ContainableDirectedGraphElement>());
		
		return subprocessNodes;
	}
	
	private Set<ContainableDirectedGraphElement> DFSForSubprocessDiscovery(
			AbstractDirectedGraphNode startNode, 
			AbstractDirectedGraphNode endNode, 
			Collection<ContainableDirectedGraphElement> processedElements) {
		
		Set<ContainableDirectedGraphElement> resultSet 
			= new HashSet<ContainableDirectedGraphElement>();
		resultSet.add((ContainableDirectedGraphElement)startNode);
		if(!startNode.equals(endNode)) {
			for (AbstractDirectedGraphEdge<?,?> outEdge : graph.getOutEdges(startNode)) {
				if (!(outEdge instanceof DataAssociation)){
					// TODO: Excplicit type cast!
					resultSet.add((ContainableDirectedGraphElement) outEdge);
					AbstractDirectedGraphNode nextNode = outEdge.getTarget();
					if (!processedElements.contains(nextNode)) {
						processedElements.add((ContainableDirectedGraphElement) nextNode);
						resultSet.addAll(DFSForSubprocessDiscovery(nextNode, endNode, processedElements));
					}
				}
			}
		}
		return resultSet;
	}
	
	
	/**
	 * Determine dominators for each node of the graph
	 * 
	 * @param directedGraph
	 * @param startNode
	 * @param inversive - true for determining post-dominators
	 * @return
	 */
	private Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> 
	determineDominatorsForGraphNodes(boolean inversive) {

		Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> mapToDominators =
				new HashMap<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>>();
		initMapToDominators(mapToDominators, graph, inversive ? endNode : startNode);
		
		boolean calculatingIsFinished = false;
		while(!calculatingIsFinished) {			
			calculatingIsFinished = true;
			for(AbstractDirectedGraphNode node : retrieveAllNodesOnTheLevel(graph)) {
				Set<AbstractDirectedGraphNode> nodePredcessors 
				= collectNodePredcessors(graph, node, inversive);
				for(AbstractDirectedGraphNode predcessor : nodePredcessors) {
					boolean elementsRemoved = intersectDominators(node, predcessor, mapToDominators);
					// If some elements have been removed, calculating of dominators has not been finished
					if(elementsRemoved) {
						calculatingIsFinished = false;
					}
				}
			}
		}
		return mapToDominators;
	}
	
	/**
	 * Construct tree of (post)dominators out of a map of (post)dominators
	 * @param map
	 * @return
	 */
	private Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> 
		constructTree(Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> map) {
		Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> resultMap = 
				new HashMap<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>>();
		
		for(AbstractDirectedGraphNode graphNode : map.keySet()) {
			for(AbstractDirectedGraphNode domGraphNode: map.get(graphNode)) {
				if(!resultMap.containsKey(domGraphNode)) {
					resultMap.put(domGraphNode, new HashSet<AbstractDirectedGraphNode>());
				}
				Set<AbstractDirectedGraphNode> setOfChildren = resultMap.get(domGraphNode);
				if(!graphNode.equals(domGraphNode)) {
					setOfChildren.add(graphNode);
				}
			}
		}
		return resultMap;
	}
	
	/**
	 * The intersection of dominator sets
	 * 
	 * @param node
	 * @param predcessor
	 * @param mapToDominators
	 * @return true if some elements have been removed
	 */
	private boolean intersectDominators(AbstractDirectedGraphNode node, 
			AbstractDirectedGraphNode predcessor, Map<AbstractDirectedGraphNode,
			Set<AbstractDirectedGraphNode>> mapToDominators) {
		
		Set<AbstractDirectedGraphNode> nodeDominators = mapToDominators.get(node);
		Set<AbstractDirectedGraphNode> predcessorDominators = mapToDominators.get(predcessor);

		if(predcessorDominators == null) {
			System.out.println(node);
			System.out.println(predcessor);
		}
		boolean newSetOfDominators = false;
		Set<AbstractDirectedGraphNode> elementsToPut = new HashSet<AbstractDirectedGraphNode>();
		for (AbstractDirectedGraphNode nodeDominator : nodeDominators) {
			if ((predcessorDominators.contains(nodeDominator)) || (nodeDominator.equals(node))) {
				elementsToPut.add(nodeDominator);
			}
			else {
				newSetOfDominators = true;
			}
		}
		
		mapToDominators.put(node, elementsToPut);

		return newSetOfDominators;
	}
	
	/**
	 * Collect node predcessors
	 * 
	 * @param directedGraph
	 * @param startNode
	 * @return
	 */
	private Set<AbstractDirectedGraphNode> collectNodePredcessors
		(DirectedGraph<? extends AbstractDirectedGraphNode, 
			? extends AbstractDirectedGraphEdge<?,?>> directedGraph, AbstractDirectedGraphNode node,
			boolean inversive) {
		
		Set<AbstractDirectedGraphNode> nodePredcessors = new HashSet<AbstractDirectedGraphNode>();
		if (!inversive) {
			Collection<? extends AbstractDirectedGraphEdge<?,?>> inEdges = directedGraph.getInEdges(node);
			for (AbstractDirectedGraphEdge<? extends AbstractDirectedGraphNode, 
					? extends AbstractDirectedGraphNode> inEdge : inEdges) {
				if (!(inEdge instanceof DataAssociation)) {
					nodePredcessors.add(inEdge.getSource());
				}
			}
		} else {
			Collection<? extends AbstractDirectedGraphEdge<?,?>> outEdges = directedGraph.getOutEdges(node);
			for (AbstractDirectedGraphEdge<? extends AbstractDirectedGraphNode, 
					? extends AbstractDirectedGraphNode> outEdge : outEdges) {
				if (!(outEdge instanceof DataAssociation)) {
					nodePredcessors.add(outEdge.getTarget());
				}
			}
		}
		
		return nodePredcessors;
	}

	/*
	 * Retrieve all the nodes on tha same level as the start node
	 */
	@SuppressWarnings("unchecked")
	private Set<AbstractDirectedGraphNode> retrieveAllNodesOnTheLevel(
			DirectedGraph<? extends AbstractDirectedGraphNode, ? extends AbstractDirectedGraphEdge<?, ?>> directedGraph) {

		Set<AbstractDirectedGraphNode> resultSet = new HashSet<AbstractDirectedGraphNode>();

		for (AbstractDirectedGraphNode node : directedGraph.getNodes()) {
			if (node instanceof ContainableDirectedGraphElement) {
				// TODO: Make something more general
				if (node instanceof BPMNNode) {
					if (((BPMNNode) node).getParentSubProcess() == parentElement && !(node instanceof Swimlane)) {
						resultSet.add(node);
					}
				} else if (node instanceof ContainableDirectedGraphElement) {
					if (((ContainableDirectedGraphElement) node).getParent() == parentElement) {
						resultSet.add(node);
					}

				} else {
					resultSet.add(node);
				}
			}
		}
		return resultSet;
	}
	/** 
	 * Initialize map to dominators
	 * 
	 * @param mapToDominators
	 * @param directedGraph
	 * @param startNode
	 */
	private void initMapToDominators(Map<AbstractDirectedGraphNode, 
			Set<AbstractDirectedGraphNode>> mapToDominators, 
			DirectedGraph<? extends AbstractDirectedGraphNode,
					? extends AbstractDirectedGraphEdge<?,?>> directedGraph, 
			AbstractDirectedGraphNode startNode) {
		
		// All nodes of the graph
		final Set<AbstractDirectedGraphNode> ALL = 
				new HashSet<AbstractDirectedGraphNode>(retrieveAllNodesOnTheLevel(directedGraph));
		
		// Start graph node
		final Set<AbstractDirectedGraphNode> START = 
				new HashSet<AbstractDirectedGraphNode>(Arrays.asList(new AbstractDirectedGraphNode[] {startNode}));
		
		// Dominators should be initialized: initially we assume that every node in the graph
		// (except source node) has all other graph nodes as dominators
		for(AbstractDirectedGraphNode node : retrieveAllNodesOnTheLevel(directedGraph)) {
			if(node.equals(startNode)) {
				mapToDominators.put(node, START);
			} else {
				mapToDominators.put(node, ALL);
			}
		}	
	}
}