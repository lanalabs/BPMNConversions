package org.processmining.plugins.converters;

import java.util.Map;

import org.processmining.framework.connections.impl.AbstractConnection;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;

public class BPMNConversionConnection extends AbstractConnection {
	
	public static String BPMN_DIAGRAM = "BPMN Diagram";
	public static String PETRI_NET = "Petri net";
	public static String CONVERSION_MAP = "Conversion map";
	
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			Map<String, Activity> conversionMap) {
		super(label);
		put(BPMN_DIAGRAM, bpmnDiagram);
		put(PETRI_NET, petriNet);
		put(CONVERSION_MAP, conversionMap);
	}
}
