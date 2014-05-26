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
public class DFS {

	public DFS (DirectedGraph graph, DirectedGraphNode startNode) {
		this.graph = graph;
		runDFS(startNode);
	}
	
	private final DirectedGraph graph;
	
	
	// Map of descendants
	private  Map<DirectedGraphNode, Set<DirectedGraphNode>> descendants 
		= new HashMap<DirectedGraphNode, Set<DirectedGraphNode>>();
	
	// Visited nodes
	private Set<DirectedGraphNode> visitedNodes = new HashSet<DirectedGraphNode>();
	
	/**
	 * Identify descendants of the node
	 * 
	 * @param node
	 * @return
	 */
	public Set<DirectedGraphNode> findDescendants(DirectedGraphNode node) {
		if(descendants.get(node) != null) {
			return descendants.get(node);
		} else {
			return new HashSet<DirectedGraphNode>();
		}
	}
	
	/**
	 * Run DFS
	 * 
	 * @param startNode
	 * @return
	 */
	private void runDFS(DirectedGraphNode currentNode) {
		
		Set<DirectedGraphNode> nodeDescendants = descendants.get(currentNode);
		if (nodeDescendants == null) {
			nodeDescendants = new HashSet<DirectedGraphNode>();
			descendants.put(currentNode, nodeDescendants);
		}
		
		for (Object outEdge : graph.getOutEdges(currentNode)) {
			DirectedGraphNode nextNode = (DirectedGraphNode) (((DirectedGraphEdge) outEdge).getTarget());
			nodeDescendants.add(nextNode);
			if (descendants.get(nextNode) != null) {
				nodeDescendants.addAll(descendants.get(nextNode));
			}
			for(Object node : graph.getNodes()) {
				if ((descendants.get(node) != null ) && (descendants.get(node).contains(currentNode))) {
					descendants.get(node).add(nextNode);
					if (descendants.get(nextNode) != null) {
						descendants.get(node).addAll(descendants.get(nextNode));
					}
				}
			}
			
			if (!visitedNodes.contains(nextNode)) {
				visitedNodes.add(nextNode);
				runDFS(nextNode);
			}
		}
	}
}
