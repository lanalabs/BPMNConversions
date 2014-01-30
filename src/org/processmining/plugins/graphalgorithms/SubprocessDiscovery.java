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
import org.processmining.models.graphbased.directed.DirectedGraph;

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
	
	/**
	 * Post-dominators for all diagram nodes
	 */
	private Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> postDominators;
	
	public SubprocessDiscovery(DirectedGraph<? extends AbstractDirectedGraphNode, 
			? extends AbstractDirectedGraphEdge<?,?>> directedGraph, AbstractDirectedGraphNode startNode,
			AbstractDirectedGraphNode endNode) {
		
		this.graph = directedGraph;
		this.startNode = startNode;
		this.endNode = endNode;
		this.dominators = determineDominatorsForGraphNodes(false);
		this.postDominators = determineDominatorsForGraphNodes(true);
		
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
		
		// Determine the immediate dominator for the set of node
		AbstractDirectedGraphNode immediateDominator = commonDominators.get(0);
		for(AbstractDirectedGraphNode dominator : commonDominators) {
			if((inversive ? postDominators : dominators).get(dominator).contains(immediateDominator)) {
				immediateDominator = dominator;
			}
		}
		
		return immediateDominator;
	}
	
	public Set<ContainableDirectedGraphElement> determineSubprocessElements(
			AbstractDirectedGraphNode subprocessStartNode, 
			AbstractDirectedGraphNode subprocessEndNode) {
		
		Set<ContainableDirectedGraphElement> subprocessNodes
			= DFSForSubprocessDiscovery(subprocessStartNode, subprocessEndNode, 
					new HashSet<ContainableDirectedGraphElement>());
		
		return subprocessNodes;
	}
	
	/**
	 * Identify subrocess contents with DFS-algorithm
	 * 
	 * @param subprocessStartNode
	 * @param subprocessEndNode
	 * @param processedElements
	 * @return
	 */
	private Set<ContainableDirectedGraphElement> DFSForSubprocessDiscovery(
			AbstractDirectedGraphNode startNode, 
			AbstractDirectedGraphNode endNode, 
			Collection<ContainableDirectedGraphElement> processedElements) {
		
		Set<ContainableDirectedGraphElement> resultSet 
			= new HashSet<ContainableDirectedGraphElement>();
		resultSet.add((ContainableDirectedGraphElement)startNode);
		if(!startNode.equals(endNode)) {
			for (AbstractDirectedGraphEdge<?,?> outEdge : graph.getOutEdges(startNode)) {
				// TODO: Excplicit type cast!
				resultSet.add((ContainableDirectedGraphElement) outEdge);
				AbstractDirectedGraphNode nextNode = outEdge.getTarget();
				if (!processedElements.contains(nextNode)) {
					processedElements.add((ContainableDirectedGraphElement) nextNode);
					resultSet.addAll(DFSForSubprocessDiscovery(nextNode, endNode, processedElements));
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
	public Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> 
	determineDominatorsForGraphNodes(boolean inversive) {

		Map<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>> mapToDominators =
				new HashMap<AbstractDirectedGraphNode, Set<AbstractDirectedGraphNode>>();
		initMapToDominators(mapToDominators, graph, inversive ? endNode : startNode);
		
		boolean calculatingIsFinished = false;
		while(!calculatingIsFinished) {			
			calculatingIsFinished = true;
			for(AbstractDirectedGraphNode node : graph.getNodes()) {
				Set<AbstractDirectedGraphNode> nodePredcessors 
				= collectNodePredcessors(graph, node, inversive);
				System.out.println("Node " + node);
				System.out.println("Node predsessors" + nodePredcessors);
				for(AbstractDirectedGraphNode predcessor : nodePredcessors) {
					boolean elementsRemoved = intersectDominators(node, predcessor, mapToDominators);
					// If some elements have been removed, calculating of dominators has not been finished
					if(elementsRemoved) {
						calculatingIsFinished = false;
					}
				}
			}
		}
		System.out.println(mapToDominators);
		return mapToDominators;
	}
	
	/**
	 * The intersection of dominator sets
	 * 
	 * @param node
	 * @param predcessor
	 * @param mapToDominators
	 * @return true if some elements have been removed
	 */
	private static boolean intersectDominators(AbstractDirectedGraphNode node, 
			AbstractDirectedGraphNode predcessor, Map<AbstractDirectedGraphNode,
			Set<AbstractDirectedGraphNode>> mapToDominators) {
		
		Set<AbstractDirectedGraphNode> nodeDominators = mapToDominators.get(node);
		Set<AbstractDirectedGraphNode> predcessorDominators = mapToDominators.get(predcessor);

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
	private static Set<AbstractDirectedGraphNode> collectNodePredcessors
		(DirectedGraph<? extends AbstractDirectedGraphNode, 
			? extends AbstractDirectedGraphEdge<?,?>> directedGraph, AbstractDirectedGraphNode node,
			boolean inversive) {
		
		Set<AbstractDirectedGraphNode> nodePredcessors = new HashSet<AbstractDirectedGraphNode>();
		if (!inversive) {
			Collection<? extends AbstractDirectedGraphEdge<?,?>> inEdges = directedGraph.getInEdges(node);
			for (AbstractDirectedGraphEdge<? extends AbstractDirectedGraphNode, 
					? extends AbstractDirectedGraphNode> inEdge : inEdges) {
				nodePredcessors.add(inEdge.getSource());
			}
		} else {
			Collection<? extends AbstractDirectedGraphEdge<?,?>> outEdges = directedGraph.getOutEdges(node);
			for (AbstractDirectedGraphEdge<? extends AbstractDirectedGraphNode, 
					? extends AbstractDirectedGraphNode> outEdge : outEdges) {
				nodePredcessors.add(outEdge.getTarget());
			}
		}
		
		return nodePredcessors;
	}
	
	/**
	 * Initialize map to dominators
	 * 
	 * @param mapToDominators
	 * @param directedGraph
	 * @param startNode
	 */
	private static void initMapToDominators(Map<AbstractDirectedGraphNode, 
			Set<AbstractDirectedGraphNode>> mapToDominators, 
			DirectedGraph<? extends AbstractDirectedGraphNode,
					? extends AbstractDirectedGraphEdge<?,?>> directedGraph, 
			AbstractDirectedGraphNode startNode) {
		
		// All nodes of the graph
		final Set<AbstractDirectedGraphNode> ALL = 
				new HashSet<AbstractDirectedGraphNode>(directedGraph.getNodes());
		
		// Start graph node
		final Set<AbstractDirectedGraphNode> START = 
				new HashSet<AbstractDirectedGraphNode>(Arrays.asList(new AbstractDirectedGraphNode[] {startNode}));
		
		// Dominators should be initialized: initially we assume that every node in the graph
		// (except source node) has all other graph nodes as dominators
		for(AbstractDirectedGraphNode node : directedGraph.getNodes()) {
			if(node.equals(startNode)) {
				mapToDominators.put(node, START);
			} else {
				mapToDominators.put(node, ALL);
			}
		}	
	}
}