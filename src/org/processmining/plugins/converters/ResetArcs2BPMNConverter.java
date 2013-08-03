package org.processmining.plugins.converters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.AbstractDirectedGraphEdge;
import org.processmining.models.graphbased.directed.AbstractDirectedGraphNode;
import org.processmining.models.graphbased.directed.ContainableDirectedGraphElement;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Event.EventType;
import org.processmining.models.graphbased.directed.bpmn.elements.SubProcess;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.ResetArc;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.plugins.graphalgorithms.BasicBlockDiscovery;

/**
 * Addition of cancellation regions to BPMN diagram
 *
 * @author Anna Kalenkova
 * Aug 1, 2013
 */
@Plugin(name = "Add cancellation regions to BPMN diagram", parameterLabels = { "BPMN Diagram" }, 
returnLabels = { "BPMN Diagram" }, returnTypes = { BPMNDiagram.class }, 
userAccessible = true, help = "Adds cancellation regions to BPMN diagram")
public class ResetArcs2BPMNConverter {

	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Add cancellation regions to BPMN diagram", 
		requiredParameterLabels = { 0 })
	public BPMNDiagram addCancellationRegions(UIPluginContext context, BPMNDiagram bpmnDiagram) {
		
		PetrinetGraph petriNet = obtainPetriNet(context, bpmnDiagram);
		if(petriNet == null) {
			return null;
		}
		Map<String, Activity> conversionMap = obtainConversionMap(context, bpmnDiagram);
		if(conversionMap == null) {
			return null;
		}
		Event startEvent = retrieveStartEvent(context, bpmnDiagram);
		if(startEvent == null) {
			return null;
		}
		Event endEvent = retrieveEndEvent(context, bpmnDiagram);
		if(endEvent == null) {
			return null;
		}
			
		for (Transition transition : petriNet.getTransitions()) {
			Collection<Transition> cancelledTransitions = retrieveCancelledTransitions(petriNet, transition);
			if ((cancelledTransitions != null) && (cancelledTransitions.size() > 1)) {
				Collection<Activity> cancelledActivities = convertTransitionsToActivities(cancelledTransitions,
						conversionMap);
				Activity catchingActivity = convertTransitionsToActivities(Arrays.asList(transition), conversionMap)
						.get(0);
				constructSubProcess(cancelledActivities, catchingActivity, conversionMap, bpmnDiagram, startEvent,
						endEvent);
			}
		}
		
		return bpmnDiagram;
	}
	
	private Event retrieveStartEvent(UIPluginContext context, BPMNDiagram bpmnDiagram) {
		
		List<Event> startEvents = new ArrayList<Event>();
		
		for(Event event : bpmnDiagram.getEvents()) {
			if(event.getEventType().equals(EventType.START)) {
				startEvents.add(event);
			}
		}
		if(startEvents.size() > 1) {
			String connectionCannotBeObtaintMessage = "BPMN diagram contains more "
					+ "than one start event";
			context.getFutureResult(0).cancel(true);
			context.log(connectionCannotBeObtaintMessage);
			JPanel warningPanel = new JPanel();
			warningPanel.add(new JLabel(connectionCannotBeObtaintMessage));
			context.showWizard("Add cancellation regions to BPMN diagram", true, true, warningPanel);
			return null;
		}
		return startEvents.get(0);
	}
	
	private Event retrieveEndEvent(UIPluginContext context, BPMNDiagram bpmnDiagram) {
		
		List<Event> endEvents = new ArrayList<Event>();
		
		for(Event event : bpmnDiagram.getEvents()) {
			if(event.getEventType().equals(EventType.END)) {
				endEvents.add(event);
			}
		}
		if(endEvents.size() > 1) {
			String connectionCannotBeObtaintMessage = "BPMN diagram contains more "
					+ "than one end event";
			context.getFutureResult(0).cancel(true);
			context.log(connectionCannotBeObtaintMessage);
			JPanel warningPanel = new JPanel();
			warningPanel.add(new JLabel(connectionCannotBeObtaintMessage));
			context.showWizard("Add cancellation regions to BPMN diagram", true, true, warningPanel);
			return null;
		}
		return endEvents.get(0);
	}
	
	/**
	 * Conversion of Petri net transitions to BPMN activities
	 * 
	 * @param transitions
	 * @param conversionMap
	 * @return
	 */
	private List<Activity> convertTransitionsToActivities(Collection<Transition> transitions, 
			Map<String, Activity> conversionMap) {
		
		List<Activity> resultActivitySet = new ArrayList<Activity>();
		
		for(Transition transition : transitions) {
			resultActivitySet.add(conversionMap.get(transition.getId().toString()));
		}
		
		return resultActivitySet;
	}
	
	/**
	 * Construct BPMN Subprocess which might be cancelled
	 * 
	 * @param cancelledActivities
	 * @param catchingActivity
	 * @param conversionMap
	 * @param bpmnDiagram
	 * @param startEvent
	 * @param endEvent
	 * @return the novel BPMN diagram
	 */
	private BPMNDiagram constructSubProcess(Collection<Activity>cancelledActivities, 
			Activity catchingActivity, Map<String, Activity> conversionMap, BPMNDiagram bpmnDiagram,
			Event startEvent, Event endEvent) {

		// Determine inner nodes (nodes to be included in subprocess)
		List<AbstractDirectedGraphNode> innerNodes = new ArrayList<AbstractDirectedGraphNode>();
		innerNodes.addAll(cancelledActivities);
		Collection<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>> inEdges =
				bpmnDiagram.getInEdges(catchingActivity);
		// �atching activity has only one incoming edge by the construction
		AbstractDirectedGraphEdge errorEdge = inEdges.iterator().next(); 
		innerNodes.add((AbstractDirectedGraphNode)errorEdge.getSource());
		bpmnDiagram.removeEdge(errorEdge);
		
		BasicBlockDiscovery basicBlockDiscovery = new BasicBlockDiscovery(bpmnDiagram, startEvent, endEvent);
		
		// Determine immediate common dominator
		AbstractDirectedGraphNode immCommonDominator = basicBlockDiscovery
				.determineImmediateCommonDominator(innerNodes, false);
		
		System.out.println("Common domnator " + immCommonDominator);
		
		// Determine immediate common post-dominator
		AbstractDirectedGraphNode immCommonPostDominator = basicBlockDiscovery
				.determineImmediateCommonDominator(innerNodes, true);
		
		System.out.println("Common post-domnator " + immCommonPostDominator);
		
		// Determine subprocess nodes
		Set<ContainableDirectedGraphElement> subprocessNodes = basicBlockDiscovery
				.determineSubprocessElements(immCommonDominator, immCommonPostDominator);
		
		// Create subprocess
		SubProcess subprocess = bpmnDiagram.addSubProcess("Sub-Process", false, false, false, false, false);
		for(ContainableDirectedGraphElement bpmnNode : subprocessNodes) {
			System.out.println("Add child");
			System.out.println(bpmnNode);
			subprocess.addChild((ContainableDirectedGraphElement)bpmnNode);
		}
		
		return bpmnDiagram;
	}
		
	/**
	 * Retrieving cancelled transitions - set of transitions which might be cancelled
	 * 
	 * @param petriNet
	 * @param transition - transition which treated as catching transition
	 * @return
	 */
	private Set<Transition> retrieveCancelledTransitions(PetrinetGraph petriNet, Transition transition) {

		Set<Transition> resultSetOfTransitions = new HashSet<Transition>();

		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> arcsToTransition = petriNet
				.getInEdges(transition);
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inArc : arcsToTransition) {
			if (inArc instanceof ResetArc) {
				Place place = (Place) inArc.getSource();
				Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> arcsFromPlace = petriNet
						.getOutEdges(place);
				for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> outArc : arcsFromPlace) {
					if (!(outArc instanceof ResetArc)) {
						resultSetOfTransitions.add((Transition) outArc.getTarget());
					}
				}
			}
		}

		return resultSetOfTransitions;
	}
	
	/**
	 * Obtain Petri net for BPMN diagram
	 * 
	 * @param context
	 * @param bpmnDiagram
	 * @return
	 */
	private PetrinetGraph obtainPetriNet(UIPluginContext context, BPMNDiagram bpmnDiagram) {
		
		PetrinetGraph petriNet = null;
		try {
				petriNet = context.tryToFindOrConstructFirstObject(PetrinetGraph.class, 
					BPMNConversionConnection.class, BPMNConversionConnection.PETRI_NET, bpmnDiagram);
			} catch (ConnectionCannotBeObtained e) {
				String connectionCannotBeObtaintMessage = "Connection to Petri net cannot be obtaint";
				context.getFutureResult(0).cancel(true);
				context.log(connectionCannotBeObtaintMessage);
				JPanel warningPanel = new JPanel();
				warningPanel.add(new JLabel(connectionCannotBeObtaintMessage));
				context.showWizard("Add cancellation regions to BPMN diagram", true, true, warningPanel);
				return null;
			}
		return petriNet;
	}
	
	/**
	 * Obtain Conversion map for BPMN diagram activities
	 * 
	 * @param context
	 * @param bpmnDiagram
	 * @return
	 */
	private Map<String, Activity> obtainConversionMap(UIPluginContext context, BPMNDiagram bpmnDiagram) {
		
		Map<String, Activity> conversionMap = null;
		try {
				conversionMap = context.tryToFindOrConstructFirstObject(Map.class, 
						BPMNConversionConnection.class, 
						BPMNConversionConnection.CONVERSION_MAP, bpmnDiagram);
			} catch (ConnectionCannotBeObtained e) {
				String connectionCannotBeObtaintMessage = "Connection to Conversion map cannot be obtaint";
				context.getFutureResult(0).cancel(true);
				context.log(connectionCannotBeObtaintMessage);
				JPanel warningPanel = new JPanel();
				warningPanel.add(new JLabel(connectionCannotBeObtaintMessage));
				context.showWizard("Add cancellation regions to BPMN diagram", true, true, warningPanel);
				return null;
			}
		return conversionMap;
	}
}