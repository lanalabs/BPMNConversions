package org.processmining.plugins.converters;

import java.util.Map;

import org.processmining.framework.connections.impl.AbstractConnection;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataPetriNet;
import org.processmining.processtree.ProcessTree;

public class BPMNConversionConnection extends AbstractConnection {
	
	public static String BPMN_DIAGRAM = "BPMN Diagram";
	public static String PETRI_NET = "Petri net";
	public static String DATA_PETRI_NET = "Data Petri net";
	public static String PROCESS_TREE = "Process tree";
	public static String CONVERSION_MAP = "Process tree";
	
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			Map<String, Activity> conversionMap) {
		super(label);
		put(BPMN_DIAGRAM, bpmnDiagram);
		put(PETRI_NET, petriNet);
		put(CONVERSION_MAP, conversionMap);
	}
	
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			DataPetriNet dataPetrinet, Map<String, Activity> conversionMap) {
		super(label);
		put(BPMN_DIAGRAM, bpmnDiagram);
		put(PETRI_NET, petriNet);
		put(DATA_PETRI_NET, dataPetrinet);
		put(CONVERSION_MAP, conversionMap);
	}
	
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, ProcessTree tree,
			Map<String, Activity> conversionMap) {
		super(label);
		put(BPMN_DIAGRAM, bpmnDiagram); 
		put(PROCESS_TREE, tree);
		put(CONVERSION_MAP, (conversionMap));
	}
}
