package org.processmining.plugins.converters.bpmn2pn;

import java.util.ArrayList;
import java.util.Collection;
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
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventType;
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
	
	protected List<Place> finalPlace = new ArrayList<Place>();
	
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
								t_eventStart.setInvisible(true);
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
								finalPlace.remove(p); // final place no longer part of final marking
								Transition t_eventEnd = net.addTransition("t_end_"+e.getLabel());
								t_eventEnd.setInvisible(true);
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
			finalPlace.add(p_uniqueEnd);
		}

		return errors.size() == 0;
	}
	

	private void translateEdges() {
		for (BPMNNode n : bpmn.getNodes()) {
			// select only the sequence flow edges, message flows etc are ignored
			List<Flow> inFlows = new ArrayList<Flow>();
			for (BPMNEdge<? extends BPMNNode, ? extends BPMNNode> e : bpmn.getInEdges(n)) {
				if (e instanceof Flow) inFlows.add((Flow)e);
			}

			// gateways are translated with one place per flow
			if (n instanceof Gateway)
				translateEdges(inFlows, false);
			
			// activities are translated with one place for all flows
			else if (n instanceof Activity)
				translateEdges(inFlows, true);
			
			// events are translated with one place for all flows, unless
			// configuration asks for multiple places
			else if (n instanceof Event) {
				if ( ((Event)n).getEventType() == EventType.END ) {
					translateEdges(inFlows, (config.endEventJoin == BPMN2PetriNetConverter_Configuration.EndEventJoin.XOR));
				} else {
					translateEdges(inFlows, true);					
				}
			}
		}
	}
	
	/**
	 * Translate edges of the BPMN diagram to places, updates {@link #flowMap}.
	 * 
	 * @param mergeToSinglePlace
	 *            if 'true', all flows are translated to the same place,
	 *            otherwise each flow is translated to its own separate place.
	 *            Use 'true' for example to translate implicit XOR-merge at an
	 *            activity.
	 */
	private void translateEdges(Collection<Flow> flows, boolean mergeToSinglePlace) {
		if (flows.isEmpty()) return;
		
		if (mergeToSinglePlace) {
			// assume all flows go to the same target node, create one place, all flows map to this place
			BPMNNode target = flows.iterator().next().getTarget();
			String p_label;
			if (config.labelFlowPlaces && target.getLabel() != null && !target.getLabel().isEmpty())
				p_label = getLabel(target.getLabel(), "flow_merge", "p", false);
			else
				p_label = "";
			
			Place p = net.addPlace(p_label);
			
			for (Flow f : flows)
				flowMap.put(f, p);
			
		} else {
			// create one place for each flow
			for (Flow f : flows) {
				String p_label;
				if (f.getLabel() != null && !f.getLabel().isEmpty())
					p_label = getLabel(f.getLabel(), "flow", "p", false);
				else if (config.labelFlowPlaces)
					p_label = getLabel(f.getSource().getLabel()+"_"+f.getTarget().getLabel(), "flow", "p", false);
				else
					p_label = "";
				
				Place p = net.addPlace(p_label);
				flowMap.put(f, p);
			}
		}
	}
	
	/**
	 * Connect transition t to all places representing the incoming flows of
	 * node n. For tasks, the incoming flows are translated to a single input
	 * place through {@link #translateEdges()}.
	 * 
	 * @param n
	 * @param t
	 */
	private void connectToAllInFlows(BPMNNode n, Transition t) {
		
		// gather all places representing the inflows
		Set<Place> places = new HashSet<Place>();
		// connect transition to place of incoming edge
		for (BPMNEdge<?, ?> f : bpmn.getInEdges(n)) {
			if (f instanceof Flow) {
				places.add(flowMap.get(f));
			}
		}

		// add edges from inflow places to transition
		for (Place p : places) {
			net.addArc(p, t);			
		}
	}
	
	/**
	 * Connect transition t to all places representing the outgoing flows of
	 * node n.
	 * 
	 * @param n
	 * @param t
	 */
	private void connectToAllOutFlows(BPMNNode n, Transition t) {

		// gather all places representing the outflows
		Set<Place> places = new HashSet<Place>();
		// connect transition to place of incoming edge
		for (BPMNEdge<?, ?> f : bpmn.getOutEdges(n)) {
			if (f instanceof Flow) {
				places.add(flowMap.get(f));
			}
		}

		// add edges from transition to outflow places
		for (Place p : places) {
			net.addArc(t, p);			
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
		
		Transition t = net.addTransition(getLabel(e.getLabel(), "start_event", "t", false));
		t.setInvisible(!config.makeStartEndEventsVisible);
		
		// input from new initial place
		Place p = net.addPlace(getLabel(e.getLabel()+"_initial", "start_event", "p", false));
		m.add(p);
		net.addArc(p, t);
		connectToAllOutFlows(e, t);
		
		setNodeMapFor(nodeMap, e, p, t);
	}
	
	private void translateEndEvent(Event e) {

		Transition t = net.addTransition(getLabel(e.getLabel(), "end_event", "t", false));
		t.setInvisible(!config.makeStartEndEventsVisible);
		connectToAllInFlows(e, t);
		// output to new final place
		Place p = net.addPlace(getLabel(e.getLabel()+"_ended", "end_event", "p", false));
		net.addArc(t, p);
	
		setNodeMapFor(nodeMap, e, p, t);
		finalPlace.add(p);
	}
	
	private void translateIntermediateEvent(Event e) {
		
		if (e.getEventTrigger() == EventTrigger.COMPENSATION) {
			warnings.add("This translation does not support compensation events and does not preserve compensation semantics.\n The resulting Petri net should not be used for soundness chedcking.");
		}
		
		String attachedActivity = (e.getBoundingNode() != null ? "_"+e.getBoundingNode().getLabel() : "");
		String triggerName = (e.getEventTrigger() != null) ? e.getEventTrigger().name() : "";
		String label = triggerName+"_"+e.getLabel()+attachedActivity;
		
		Transition t = net.addTransition(getLabel(label, "event", "t", false));
		t.setInvisible(!config.makeIntermediateEventsVisible);
		connectToAllInFlows(e, t);
		connectToAllOutFlows(e, t);

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

		Transition t_act = net.addTransition(getLabel(a.getLabel(), "task", "t", true));
		Transition t_start = null;
		Transition t_end = null;
		Place p_ready = null;
		Place p_finished = null;

		// create atomic or structured activity
		boolean model_structured = !translateActivityAtomic(a);
		if (model_structured) {
			t_start = net.addTransition(getLabel(a.getLabel()+"_start", "task", "t", true));
			t_end = net.addTransition(getLabel(a.getLabel()+"_complete", "task", "t", true));
			p_ready = net.addPlace(getLabel(a.getLabel()+"_ready", "task", "p", true));
			p_finished = net.addPlace(getLabel(a.getLabel()+"_finished", "task", "p", true));
			
			if (!config.translateWithLifeCycleVisible) {
				// hide life-cycle transitions if translation of activity shall be atomic
				t_start.setInvisible(true);
				t_end.setInvisible(true);
			} else {
				// otherwise hide the activity (so only the life-cycle transitions are visible)
				t_act.setInvisible(true);
			}

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

				Transition t_repeat = net.addTransition(getLabel(a.getLabel()+"_repeat", "task", "t", true));
				t_repeat.setInvisible(!config.makeRoutingTransitionsVisible);
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
				Place p_act_wasExecuted = net.addPlace(getLabel(a.getLabel()+"_wasExecuted", "task", "p", true));
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
							finalPlace.remove(p); // final place no longer part of final marking
							net.addArc(p, t_end);
						}
					}				
				}
			}
			
		} else {
			// default case of atomic task
			nodeSet.add(t_act);
		}
		
		connectToAllInFlows(a, t_start);
		connectToAllOutFlows(a, t_end);
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
		
		Place p = net.addPlace(getLabel(g.getLabel(), "xor", "p", false));
		setNodeMapFor(nodeMap, g, p);
		
		// connect transition to place of incoming edge
		for (BPMNEdge<?, ?> f : bpmn.getInEdges(g)) {
			if (f instanceof Flow) {
				String label = f.getSource().getLabel()+"_"+g.getLabel();
				Transition t = net.addTransition(getLabel(label, "xor_merge", "t", false));
				t.setInvisible(!config.makeRoutingTransitionsVisible);
				net.addArc(t, p);
				net.addArc(flowMap.get(f), t);
				nodeMap.get(g).add(t);
			}
		}
			
		// connect transition to place of outgoing edge
		for (BPMNEdge<?, ?> f : bpmn.getOutEdges(g)) {
			if (f instanceof Flow) {
				String label = g.getLabel()+"_"+f.getTarget().getLabel();
				Transition t = net.addTransition(getLabel(label, "xor_split", "t", false));
				t.setInvisible(!config.makeRoutingTransitionsVisible);
				net.addArc(p, t);
				net.addArc(t, flowMap.get(f));
				nodeMap.get(g).add(t);
			}
		}
	}
	
	private void translateANDGateway(Gateway g) {
		Transition t = net.addTransition(getLabel(g.getLabel(), "and", "t", false));
		t.setInvisible(!config.makeRoutingTransitionsVisible);
		setNodeMapFor(nodeMap, g, t);
		
		connectToAllInFlows(g, t);
		connectToAllOutFlows(g, t);
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
			    Transition t = net.addTransition(getLabel(g.getLabel()+"_"+i, "ior_join", "t", false));
			    t.setInvisible(!config.makeRoutingTransitionsVisible);
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
			    Transition t = net.addTransition(getLabel(g.getLabel()+"_"+i, "ior_split", "t", false));
			    t.setInvisible(!config.makeRoutingTransitionsVisible);
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
	
	public List<Place> getFinalPlaces() {
		return finalPlace;
	}
	
	/**
	 * @return map from bpmn nodes to the set of Petri net nodes that represent the bpmn node
	 */
	public Map<BPMNNode, Set<PetrinetNode>> getNodeMap() {
		return nodeMap;
	}
	
	/**
	 * @return map from edges between BPMN nodes the place that represents the edge
	 */
	public Map<BPMNEdge<BPMNNode, BPMNNode>, Place> getFlowMap() {
		return flowMap;
	}
	
	private String getLabel(String originalLabel, String bpmnPrefix, String pnPrefix, boolean isActivity) {
		switch (config.labelNodesWith) {
			case ORIGINAL_LABEL: return originalLabel;
			case PREFIX_NONTASK_BY_BPMN_TYPE:
				if (isActivity) return originalLabel;
				else return bpmnPrefix+"_"+originalLabel;
			case PREFIX_ALL_BY_BPMN_TYPE:
				return bpmnPrefix+"_"+originalLabel;
			case PREFIX_ALL_BY_PN_BPMN_TYPE:
				return pnPrefix+"_"+bpmnPrefix+"_"+originalLabel;
		}
		return originalLabel;
	}
	
}
