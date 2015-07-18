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
	public static String BPMN_DIAGRAM_WITH_SUBPROCESSES = "BPMN Diagram with Subprocesses";
	public static String PETRI_NET = "Petri net";
	public static String DATA_PETRI_NET = "Data Petri net";
	public static String PROCESS_TREE = "Process tree";
	public static String CONVERSION_MAP = "Conversion map";
	
	private BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, Map<String, Activity> conversionMap) {
        super(label);
        if (bpmnDiagram == null) throw new IllegalArgumentException("'bpmnDiagram' is null");
        if (conversionMap == null) throw new IllegalArgumentException("'conversionMap' is null");
        put(BPMN_DIAGRAM, bpmnDiagram);
        put(CONVERSION_MAP, conversionMap);
    }

    public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			Map<String, Activity> conversionMap) {
		this(label, bpmnDiagram, conversionMap);

        if (petriNet == null) throw new IllegalArgumentException("'petriNet' is null");
		put(PETRI_NET, petriNet);
	}
    
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			Map<String, Activity> conversionMap, boolean withSubProc) {

		super(label);

		if (bpmnDiagram == null)
			throw new IllegalArgumentException("'bpmnDiagram' is null");
		if (withSubProc) {
			put(BPMN_DIAGRAM_WITH_SUBPROCESSES, bpmnDiagram);
		} else {
			put(BPMN_DIAGRAM, bpmnDiagram);
		}
		put(CONVERSION_MAP, conversionMap);
		if (petriNet == null)
			throw new IllegalArgumentException("'petriNet' is null");
		put(PETRI_NET, petriNet);
	}
	
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			DataPetriNet dataPetrinet, Map<String, Activity> conversionMap) {
		this(label, bpmnDiagram, conversionMap);

        if (petriNet == null) throw new IllegalArgumentException("'petriNet' is null");
        if (dataPetrinet == null) throw new IllegalArgumentException("'dataPetriNet' is null");

        put(PETRI_NET, petriNet);
		put(DATA_PETRI_NET, dataPetrinet);
	}
	
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, ProcessTree tree,
			Map<String, Activity> conversionMap) {
		this(label, bpmnDiagram, conversionMap);

        if (tree == null) throw new IllegalArgumentException("'tree' is null");

		put(PROCESS_TREE, tree);
	}
}
