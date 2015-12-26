package org.processmining.plugins.converters;

import java.util.Map;

import org.processmining.framework.connections.impl.AbstractConnection;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataPetriNet;
import org.processmining.processtree.ProcessTree;

public class BPMNConversionConnection extends AbstractConnection {
	
	public static String BPMN_DIAGRAM = "BPMN Diagram";
	public static String BPMN_DIAGRAM_WITH_SUBPROCESSES = "BPMN Diagram with Subprocesses";
	public static String PETRI_NET = "Petri net";
	public static String DATA_PETRI_NET = "Data Petri net";
	public static String PROCESS_TREE = "Process tree";
	public static String TRANSITION_CONVERSION_MAP = "Transition conversion map";
	public static String PLACE_CONVERSION_MAP = "Place conversion map";
	
	private BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, Map<String, Activity> transitionConversionMap,
			Map<Place, Flow> placeConversionMap) {
        super(label);
        if (bpmnDiagram == null) throw new IllegalArgumentException("'bpmnDiagram' is null");
        if (transitionConversionMap == null) throw new IllegalArgumentException("'transitionConversionMap' is null");
        if (placeConversionMap == null) throw new IllegalArgumentException("'placeConversionMap' is null");
        put(BPMN_DIAGRAM, bpmnDiagram);
        put(TRANSITION_CONVERSION_MAP, transitionConversionMap);
        put(PLACE_CONVERSION_MAP, placeConversionMap);
    }

    public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			Map<String, Activity> transitionConversionMap, Map<Place, Flow> placeConversionMap) {
		this(label, bpmnDiagram, transitionConversionMap, placeConversionMap);

        if (petriNet == null) throw new IllegalArgumentException("'petriNet' is null");
		put(PETRI_NET, petriNet);
	}
    
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			Map<String, Activity> transitionConversionMap, Map<Place, Flow> placeConversionMap, boolean withSubProc) {

		super(label);

		if (bpmnDiagram == null)
			throw new IllegalArgumentException("'bpmnDiagram' is null");
		if (withSubProc) {
			put(BPMN_DIAGRAM_WITH_SUBPROCESSES, bpmnDiagram);
		} else {
			put(BPMN_DIAGRAM, bpmnDiagram);
		}
		put(TRANSITION_CONVERSION_MAP, transitionConversionMap);
		put(PLACE_CONVERSION_MAP, placeConversionMap);
		if (petriNet == null)
			throw new IllegalArgumentException("'petriNet' is null");
		put(PETRI_NET, petriNet);
	}
	
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, PetrinetGraph petriNet,
			DataPetriNet dataPetrinet, Map<String, Activity> conversionMap, Map<Place, Flow> placeConversionMap) {
		this(label, bpmnDiagram, conversionMap, placeConversionMap);

        if (petriNet == null) throw new IllegalArgumentException("'petriNet' is null");
        if (dataPetrinet == null) throw new IllegalArgumentException("'dataPetriNet' is null");

        put(PETRI_NET, petriNet);
		put(DATA_PETRI_NET, dataPetrinet);
	}
	
	public BPMNConversionConnection(String label, BPMNDiagram bpmnDiagram, ProcessTree tree,
			Map<String, Activity> conversionMap, Map<Place, Flow> placeConversionMap) {
		this(label, bpmnDiagram, conversionMap, placeConversionMap);

        if (tree == null) throw new IllegalArgumentException("'tree' is null");

		put(PROCESS_TREE, tree);
	}
}
