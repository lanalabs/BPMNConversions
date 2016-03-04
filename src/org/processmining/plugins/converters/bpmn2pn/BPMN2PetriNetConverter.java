package org.processmining.plugins.converters.bpmn2pn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.processmining.models.graphbased.directed.ContainableDirectedGraphElement;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventTrigger;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.bpmn.elements.SubProcess;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;

/**
 * Convert a BPMN model to a Petri net , only considering the control-flow of the model
 *
 * @author Dirk Fahland
 * Jul 18, 2013
 */
public class BPMN2PetriNetConverter {
	
//	private static final String EXCLUSIVE_GATEWAY = "Exclusive gateway";
//	private static final String PARALLEL_GATEWAY = "Parallel gateway";
//	private static final String EMPTY = "Empty";
	
	protected BPMNDiagram bpmn;
	protected Petrinet net;
	protected Marking m;
	
	protected List<String> warnings = new ArrayList<String>();
	protected List<String> errors = new ArrayList<String>();
	
	/**
	 * maps each BPMN control-flow edge to a place
	 */
	private Map<BPMNEdge<BPMNNode, BPMNNode>, Place> flowMap = new HashMap<BPMNEdge<BPMNNode, BPMNNode>, Place>();
	/**
	 * maps each BPMN node to a set of Petri net nodes (transitions and places)
	 */
	private Map<BPMNNode, Set<PetrinetNode>> nodeMap = new HashMap<BPMNNode, Set<PetrinetNode>>();
	
	protected BPMN2PetriNetConverter_Configuration config; // visible to subclasses using this conversion

	
	public BPMN2PetriNetConverter(BPMNDiagram bpmn, BPMN2PetriNetConverter_Configuration config) {
		this.bpmn = bpmn;
		this.config = config;
	}
	
	public BPMN2PetriNetConverter(BPMNDiagram bpmn) {
		this(bpmn, new BPMN2PetriNetConverter_Configuration());
	}

	
	public boolean convert() {
		
		net = PetrinetFactory.newPetrinet("Petri net from "+bpmn.getLabel());
		m = new Marking();

		translateEdges();
		translateEvents();
		translateActivities();
		translateSubProcesses();
		translateGateways();
		
		if (!config.linkSubProcessToActivity) {
			
			// make each subprocess executable as an alternative path:
			// add new initial/final place
			Place p_uniqueStart = net.addPlace("i");
			Place p_uniqueEnd = net.addPlace("o");
			
			for (Event e : bpmn.getEvents()) {
				switch (e.getEventType()) {
					// for each start event, create new start transition to mark initial event
					case START:
						Set<PetrinetNode> startNodes = nodeMap.get(e);
						for (PetrinetNode n : startNodes) {
							if (n instanceof Place) {
								Place p = (Place)n;
								m.remove(p); // initial place of start node is no longer initially marked
								Transition t_eventStart = net.addTransition("t_start_"+e.getLabel());
								net.addArc(p_uniqueStart, t_eventStart);
								net.addArc(t_eventStart, p);
							}
						}
						break;
					// for each end event, create new end transition to clear final event
					case END:
						Set<PetrinetNode> endNodes = nodeMap.get(e);
						for (PetrinetNode n : endNodes) {
							if (n instanceof Place) {
								Place p = (Place)n;
								Transition t_eventEnd = net.addTransition("t_end_"+e.getLabel());
								net.addArc(p, t_eventEnd);
								net.addArc(t_eventEnd, p_uniqueEnd);
							}
						}
						break;
					default:
						break;
				}
			}
			m.add(p_uniqueStart);
		}

		return errors.size() == 0;
	}
	
	/**
	 * Translate edges of the BPMN diagram to places, updates {@link #flowMap}
	 */
	private void translateEdges() {
		for (Flow f : bpmn.getFlows()) {
			Place p = net.addPlace(f.getSource().getLabel()+"_"+f.getTarget().getLabel()+"_"+f.getLabel());
			flowMap.put(f, p);
		}
	}
	
	/**
	 * Translate events to Petri net patterns, updates {@link #nodeMap} and reads {@link #flowMap}.
	 */
	private void translateEvents() {
		for (Event e : bpmn.getEvents()) {
			switch (e.getEventType()) {
				case START:
					translateStartEvent(e);
					break;
				case END:
					translateEndEvent(e);
					break;
				case INTERMEDIATE:
					translateIntermediateEvent(e);
					break;
				default:
					warnings.add("Unknown event type "+e.getEventType()+" for "+e.getId()+" ("+e.getLabel()+")");
					break;
			}
		}
	}
	
	private void translateStartEvent(Event e) {
		Place p = net.addPlace("p_start_"+e.getLabel());
		m.add(p);
		Transition t = net.addTransition("t_start_"+e.getLabel());
		net.addArc(p, t);
		// connect transition to place of outgoing edge
		for (BPMNEdge<?, ?> f : bpmn.getOutEdges(e)) {
			if (f instanceof Flow) {
				net.addArc(t, flowMap.get(f));
			}
		}
		setNodeMapFor(nodeMap, e, p, t);
	}
	
	private void translateEndEvent(Event e) {
		Place p = net.addPlace("p_end_"+e.getLabel());
		Transition t = net.addTransition("t_end_"+e.getLabel());
		net.addArc(t, p);
		// connect transition to place of incoming edge
		for (BPMNEdge<?, ?> f : bpmn.getInEdges(e)) {
			if (f instanceof Flow) {
				net.addArc(flowMap.get(f), t);
			}
		}
		setNodeMapFor(nodeMap, e, p, t);
	}
	
	private void translateIntermediateEvent(Event e) {
		
		if (e.getEventTrigger() == EventTrigger.COMPENSATION) {
			warnings.add("This translation does not support compensation events and does not preserve compensation semantics.\n The resulting Petri net should not be used for soundness chedcking.");
		}
		
		String attachedActivity = (e.getBoundingNode() != null ? "_"+e.getBoundingNode().getLabel() : "");

		Transition t = net.addTransition("t_ev_"+e.getEventTrigger().name()+"_"+e.getLabel()+attachedActivity);
		// connect transition to place of outgoing edge
		for (BPMNEdge<?, ?> f : bpmn.getInEdges(e)) {
			if (f instanceof Flow) {
				net.addArc(flowMap.get(f), t);
			}
		}
		// connect transition to place of outgoing edge
		for (BPMNEdge<?, ?> f : bpmn.getOutEdges(e)) {
			if (f instanceof Flow) {
				net.addArc(t, flowMap.get(f));
			}
		}
		setNodeMapFor(nodeMap, e, t);
	}
	
	/**
	 * Translate activities to Petri net patterns. Depending on the attributes
	 * of the activity, it is translated as atomic (single transition), or with
	 * activity life-cycle (in case of multi-instance loop behavior or attached
	 * events)
	 * 
	 * updates {@link #nodeMap} and reads {@link #flowMap}
	 */
	private void translateActivities() {
		for (Activity a : bpmn.getActivities()) {
			translateActivity(a, false);
		}
	}
	
	/**
	 * Translate activity. If the activity is a subprocess with defined inner
	 * behavior, then start and complete of the activity are linked to start event and
	 * end event of the subprocess
	 * 
	 * @param a
	 * @param linkToSubprocess
	 */
	private void translateActivity(Activity a, boolean linkToSubprocess) {
		Set<PetrinetNode> nodeSet = new HashSet<PetrinetNode>();
		nodeMap.put(a, nodeSet);

		Transition t_act = net.addTransition("t_act_"+a.getLabel());
		Transition t_start = null;
		Transition t_end = null;
		Place p_ready = null;
		Place p_finished = null;

		// create atomic or structured activity
		boolean model_structured = !translateActivityAtomic(a);
		if (model_structured) {
			t_start = net.addTransition("t_act_"+a.getLabel()+"_start");
			t_end = net.addTransition("t_act_"+a.getLabel()+"_complete");
			p_ready = net.addPlace("p_act_"+a.getLabel()+"_ready");
			p_finished = net.addPlace("p_act_"+a.getLabel()+"_finished");

			// connect start/end with activity
			net.addArc(t_start, p_ready);
			net.addArc(p_ready, t_act);
			net.addArc(t_act, p_finished);
			net.addArc(p_finished, t_end);

		} else {
			t_start = t_act;	// when connecting to the incoming/outgoing places
			t_end = t_act;
		}
		
		// there are different reasons for a structured activity
		if (model_structured) {
			
			// is a looped multi-instance activity
			if (a.isBLooped()) {

				Transition t_repeat = net.addTransition("t_act_"+a.getLabel()+"_repeat");
				// loop back
				net.addArc(p_finished, t_repeat);
				net.addArc(t_repeat, p_ready);
				
				nodeSet.add(t_act);
				nodeSet.add(t_start);
				nodeSet.add(t_end);
				nodeSet.add(t_repeat);
				nodeSet.add(p_ready);
				nodeSet.add(p_finished);
			}

			// has attached events
			Event compensationEvent = null;
			for (Event e : getBoundaryEvents(a)) {
				
				if (e.getEventTrigger() == EventTrigger.COMPENSATION) {
					compensationEvent = e;
					continue;
				}
				
				// for each boundary event: retrieve the transition representing the event
				PetrinetNode e_nodes[] = nodeMap.get(e).toArray(new PetrinetNode[nodeMap.get(e).size()]);
				// and add an arc from the activity start to the event
				net.addArc(p_ready, (Transition)e_nodes[0]);
			}

			// compensation events are translated differently
			if (compensationEvent != null) {
				// compensation events are translated not as exclusive choice, but as parallel activation
				PetrinetNode e_nodes[] = nodeMap.get(compensationEvent).toArray(new PetrinetNode[nodeMap.get(compensationEvent).size()]);
				
				// remember when the activity has been executed and enable compensation event correspondingly
				Place p_act_wasExecuted = net.addPlace("t_act_"+a.getLabel()+"_wasExecuted");
				net.addArc(t_act, p_act_wasExecuted);
				net.addArc(p_act_wasExecuted, (Transition)e_nodes[0]);
				
				nodeSet.add(p_act_wasExecuted);
			}
			
			// if activity has an inner definition of a subprocess and definition shall be linked
			if (a instanceof SubProcess && linkToSubprocess) {
				
				Set<ContainableDirectedGraphElement> children = ((SubProcess) a).getChildren();
				
				// get the start and end event of the process
				List<Event> startEvents = new LinkedList<Event>();
				List<Event> endEvents = new LinkedList<Event>();
				for (ContainableDirectedGraphElement c : children) {
					if (c instanceof Event) {
						Event e = (Event)c;
						switch (e.getEventType()) {
							case START:
								startEvents.add(e);
								break;
							case END:
								endEvents.add(e);
								break;
							default:
								// ignore
								break;
						}
					}
				}
				
				if (startEvents.size() > 1) warnings.add("Subprocess '"+a.getLabel()+"' has multiple start events. Start events are assumed to be exclusive.");
				if (endEvents.size() > 1) warnings.add("Subprocess '"+a.getLabel()+"' has multiple end events. End events are assumed to be exclusive.");
				
				// they should be translated by now, 
				// link start and end events of the subprocess to start and end transitions of this activity
				for (Event startEvent : startEvents) {
					Set<PetrinetNode> startNodes = nodeMap.get(startEvent);
					for (PetrinetNode n : startNodes) {
						if (n instanceof Place) {
							Place p = (Place)n;
							m.remove(p); // initial place of start node is no longer initially marked
							net.addArc(t_start, p);
						}
					}
				}
				
				for (Event endEvent : endEvents) {
					Set<PetrinetNode> endNodes = nodeMap.get(endEvent);
					for (PetrinetNode n : endNodes) {
						if (n instanceof Place) {
							Place p = (Place)n;
							net.addArc(p, t_end);
						}
					}				
				}
			}
			
		} else {
			// default case of atomic task
			nodeSet.add(t_act);
		}
		
		// connect transition to place of incoming edge
		for (BPMNEdge<?, ?> f : bpmn.getInEdges(a)) {
			if (f instanceof Flow) {
				net.addArc(flowMap.get(f), t_start);
			}
		}
		// connect transition to place of outgoing edge
		for (BPMNEdge<?, ?> f : bpmn.getOutEdges(a)) {
			if (f instanceof Flow) {
				net.addArc(t_end, flowMap.get(f));
			}
		}
	}
	
	
	
	/**
	 * Translate subprocesses to Petri net patterns. Currently only as atomic tasks.
	 * 
	 * updates {@link #nodeMap} and reads {@link #flowMap}
	 */
	private void translateSubProcesses() {
		for (SubProcess s : bpmn.getSubProcesses()) {
			//warnings.add("Subprocess '"+s.getLabel()+"' has been translated as activity; inner details are not considered.");
			boolean hasInnerDefinition = (s.getGraph() != null && s.getGraph() instanceof BPMNDiagram); 
			translateActivity(s, hasInnerDefinition && config.linkSubProcessToActivity);
		}
	}
	
	/**
	 * @param a
	 * @return true iff the activity can be translated as a single atomic
	 *         transition (i.e., no multi-instance looping behavior, no attached
	 *         events etc.)
	 */
	private boolean translateActivityAtomic(Activity a) {
		
		if (a.isBLooped()) return false;
		if (getBoundaryEvents(a).size() > 0) return false;
		if (a instanceof SubProcess && config.linkSubProcessToActivity) return false;
		return true;
	}
	
	/**
	 * @param a
	 * @return all events that are attached to the given activity
	 */
	private List<Event> getBoundaryEvents(Activity a) {
		List<Event> boundaryEvents = new ArrayList<Event>();
		for (Event e : bpmn.getEvents()) {
			if (e.getBoundingNode() == a) boundaryEvents.add(e);
		}
		return boundaryEvents;
	}
	
	/**
	 * Translates gateways to Petri net patterns, updated {@link #nodeMap}, reads {@link #flowMap}.
	 */
	private void translateGateways() {
		for (Gateway g : bpmn.getGateways()) {
			switch (g.getGatewayType()) {
				case DATABASED:
				case EVENTBASED:
					translateXORGateway(g);
					break;
				case PARALLEL:
					translateANDGateway(g);
					break;
				case COMPLEX:
				case INCLUSIVE:
					translateORGateway(g);
					break;
				default:
					warnings.add("Unknown gateway type "+g.getGatewayType()+" for "+g.getId()+" ("+g.getLabel()+")");
					break;
			}
		}
	}
		
	private void translateXORGateway(Gateway g) {
		
		Place p = net.addPlace("g_xor_"+g.getLabel());
		setNodeMapFor(nodeMap, g, p);
		
		// connect transition to place of incoming edge
		for (BPMNEdge<?, ?> f : bpmn.getInEdges(g)) {
			if (f instanceof Flow) {
				Transition t = net.addTransition(f.getSource().getLabel()+"_merge_"+g.getLabel());
				net.addArc(t, p);
				net.addArc(flowMap.get(f), t);
				nodeMap.get(g).add(t);
			}
		}
			
		// connect transition to place of outgoing edge
		for (BPMNEdge<?, ?> f : bpmn.getOutEdges(g)) {
			if (f instanceof Flow) {
				Transition t = net.addTransition(f.getTarget().getLabel()+"_split_"+g.getLabel());
				net.addArc(p, t);
				net.addArc(t, flowMap.get(f));
				nodeMap.get(g).add(t);
			}
		}
	}
	
	private void translateANDGateway(Gateway g) {
		Transition t = net.addTransition("g_and_"+g.getLabel());
		setNodeMapFor(nodeMap, g, t);
		
		// connect transition to place of incoming edge
		for (BPMNEdge<?, ?> f : bpmn.getInEdges(g)) {
			if (f instanceof Flow) {
				net.addArc(flowMap.get(f), t);
			}
		}
		// connect transition to place of outgoing edge
		for (BPMNEdge<?, ?> f : bpmn.getOutEdges(g)) {
			if (f instanceof Flow) {
				net.addArc(t, flowMap.get(f));
			}
		}
	}
	
	private void translateORGateway(Gateway g) {
		
		Set<PetrinetNode> nodeSet = new HashSet<PetrinetNode>();
		nodeMap.put(g, nodeSet);
		
		// OR-join
		if (bpmn.getInEdges(g).size() > 1 && bpmn.getOutEdges(g).size() == 1) {
			warnings.add("Cannot translate Inclusive-OR-Join to standard Petri nets. Translation of gateway "+g.getId()+" ("+g.getLabel()+") does not preserve the semantics.");
			
			BPMNEdge<?, ?> outEdge = (BPMNEdge<?, ?>)bpmn.getOutEdges(g).toArray()[0]; 
			if (!(outEdge instanceof Flow)) {
				warnings.add("Cannot translate Inclusive-OR-Join to standard Petri nets. Gateway "+g.getId()+" ("+g.getLabel()+") has no outgoing control-flow edge.");
				return;
			}
			Place p_out = flowMap.get(outEdge);
			
			// generate a transition for each non-empty subset of the outgoing edges
			
			// get the set of all places representing outgoing edges
			Place p_ins[] = new Place[bpmn.getInEdges(g).size()];
			int ik=0;
			for (BPMNEdge<?, ?> f : bpmn.getInEdges(g)) {
				if (f instanceof Flow) {
					p_ins[ik] = flowMap.get(f);
					ik++;
				}
			}
			int n = p_ins.length;
			if (n == 0) {
				warnings.add("Cannot translate Inclusive-OR-Join to standard Petri nets. Gateway "+g.getId()+" ("+g.getLabel()+") has no incoming control-flow edge.");
				return;
			}
			
			// then compute all subsets by counting to 2^n and using the bitmask of
			// the number to tell which places to include in the subset
			for(int i = 0; i < (1<<n); i++){
				List<Place> p_subset = new ArrayList<Place>();
			    for(int j = 0; j < n; j++){
			        if( ((i>>j) & 1) == 1) { 	// bit j is on
			        	p_subset.add(p_ins[j]);// add place
			        }
			    }
			    // not for the emtpy subset
			    if (p_subset.isEmpty()) continue;
			    
			    // create transition for this subset and connect it to the post-places in the subset
			    Transition t = net.addTransition("g_ior_join_"+g.getLabel()+"_"+i);
			    nodeSet.add(t);
			    for (Place p_in : p_subset) {
			    	net.addArc(p_in, t);
			    }
			    net.addArc(t, p_out);
			}
			
		// OR-split with one incoming edge
		} else {

			BPMNEdge<?, ?> inEdge = (BPMNEdge<?, ?>)bpmn.getInEdges(g).toArray()[0]; 
			if (!(inEdge instanceof Flow)) {
				warnings.add("Cannot translate Inclusive-OR-Join to standard Petri nets. Gateway "+g.getId()+" ("+g.getLabel()+") has no incoming control-flow edge.");
				return;
			}			
			Place p_in = flowMap.get(inEdge);
			
			// generate a transition for each non-empty subset of the outgoing edges
			
			// get the set of all places representing outgoing edges
			Place p_outs[] = new Place[bpmn.getOutEdges(g).size()];
			int ik=0;
			for (BPMNEdge<?, ?> f : bpmn.getOutEdges(g)) {
				p_outs[ik] = flowMap.get(f);
				ik++;
			}
			int n = p_outs.length;
			if (n == 0) {
				warnings.add("Cannot translate Inclusive-OR-Join to standard Petri nets. Gateway "+g.getId()+" ("+g.getLabel()+") has no outgoing control-flow edge.");
				return;
			}
			
			// then compute all subsets by counting to 2^n and using the bitmask of
			// the number to tell which places to include in the subset
			for(int i = 0; i < (1<<n); i++){
				List<Place> p_subset = new ArrayList<Place>();
			    for(int j = 0; j < n; j++){
			        if( ((i>>j) & 1) == 1) { 	// bit j is on
			        	p_subset.add(p_outs[j]);// add place
			        }
			    }
			    // not for the emtpy subset
			    if (p_subset.isEmpty()) continue;
			    
			    // create transition for this subset and connect it to the post-places in the subset
			    Transition t = net.addTransition("g_ior_split_"+g.getLabel()+"_"+i);
			    nodeSet.add(t);
			    for (Place p_out : p_subset) {
			    	net.addArc(t, p_out);
			    }
			    net.addArc(p_in, t);
			}
			
		}
	}
	

	/**
	 * Extend nodeMap with a new entry for n containing all nodes.
	 * @param nodeMap
	 * @param n
	 * @param nodes
	 */
	private void setNodeMapFor(Map<BPMNNode, Set<PetrinetNode>> nodeMap, BPMNNode n, PetrinetNode ...nodes) {
		Set<PetrinetNode> nodeSet = new HashSet<PetrinetNode>();
		for (PetrinetNode n2 : nodes)
			nodeSet.add(n2);
		nodeMap.put(n, nodeSet);
	}

	/**
	 * @return resulting Petri net
	 */
	public Petrinet getPetriNet() {
		return net;
	}

	/**
	 * @return initial marking of the resulting Petri net
	 */
	public Marking getMarking() {
		return m;
	}
	
	public List<String> getWarnings() {
		return warnings;
	}
	
	public List<String> getErrors() {
		return errors;
	}
	
}
