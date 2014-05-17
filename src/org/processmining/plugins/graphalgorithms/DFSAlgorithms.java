package org.processmining.plugins.graphalgorithms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.models.graphbased.directed.DirectedGraph;
import org.processmining.models.graphbased.directed.DirectedGraphEdge;
import org.processmining.models.graphbased.directed.DirectedGraphNode;

/**
 * Standard DFS (Depth-first search) algorithms
 *
 * @author Anna Kalenkova
 * May 12, 2014
 */
@SuppressWarnings({"rawtypes", "cast"})
public class DFSAlgorithms {

	public DFSAlgorithms (DirectedGraph graph, DirectedGraphNode startNode) {
		this.graph = graph;
		this.startNode = startNode;
	}
	
	private final DirectedGraph graph;
	
	private final DirectedGraphNode startNode;
	
	// Map of ancestors
	private  Map<DirectedGraphNode, Set<DirectedGraphNode>> ancestors 
		= new HashMap<DirectedGraphNode, Set<DirectedGraphNode>>();
	
	// Visited nodes
	private Set<DirectedGraphNode> visitedNodes = new HashSet<DirectedGraphNode>();
	
	/**
	 * Identify backward edges of the graph
	 * 
	 * @return
	 */
	public Set<DirectedGraphEdge> findBackwardEdges() {
		ancestors.clear();
		visitedNodes.clear();
		return findBackwardEdgesFromStartNode(startNode);
	}
	
	/**
	 * Identify backward edges with DFS-algorithm from a start node
	 * 
	 * @param startNode
	 * @return
	 */
	private Set<DirectedGraphEdge> findBackwardEdgesFromStartNode(DirectedGraphNode startNode) {

	
		Set<DirectedGraphEdge> backwardEdges = new HashSet<DirectedGraphEdge>();
		DirectedGraphNode currentNode = startNode;
		for (Object outEdge : graph.getOutEdges(currentNode)) {
			DirectedGraphNode nextNode = (DirectedGraphNode) (((DirectedGraphEdge) outEdge).getTarget());
			Set<DirectedGraphNode> nodeAncestors = ancestors.get(nextNode);
			if (nodeAncestors == null) {
				nodeAncestors = new HashSet<DirectedGraphNode>();
				ancestors.put(nextNode, nodeAncestors);
			}
			nodeAncestors.add(currentNode);
			if(ancestors.get(currentNode) != null) {
				nodeAncestors.addAll(ancestors.get(currentNode));
			}
			if ((ancestors.get(currentNode) != null) && ancestors.get(currentNode).contains(nextNode)) {
				backwardEdges.add((DirectedGraphEdge) outEdge);
			}
			if (!visitedNodes.contains(nextNode)) {
				visitedNodes.add(nextNode);
				backwardEdges.addAll(findBackwardEdgesFromStartNode(nextNode));
			}
		}
		return backwardEdges;
	}
}
