package org.processmining.tests.converters;

import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.plugins.converters.BPMNConversionConnection;
import org.processmining.processtree.ProcessTree;

public class BPMNConversionConnectionTest {

    private final Map<String, Activity> EMPTY_TRANSITION_CONVERSION_MAP = new HashMap<String, Activity>();
    private final Map<Place, Flow> EMPTY_PLACE_CONVERSION_MAP = new HashMap<Place, Flow>();

    @Test(expected = IllegalArgumentException.class)
    public void constructorPetriNet_withNullBpmnDiagram_expectedException() {
        new BPMNConversionConnection("label", null, mock(PetrinetGraph.class), EMPTY_TRANSITION_CONVERSION_MAP,
        		EMPTY_PLACE_CONVERSION_MAP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorPetriNet_withNullPetrinetGraph_expectedException() {
        PetrinetGraph nullGraph = null;

        new BPMNConversionConnection("label", mock(BPMNDiagram.class), nullGraph, EMPTY_TRANSITION_CONVERSION_MAP,
        		EMPTY_PLACE_CONVERSION_MAP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorPetriNet_withNullConversionMap_expectedException() {
        new BPMNConversionConnection("label", mock(BPMNDiagram.class), mock(PetrinetGraph.class), null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorDataPetriNet_withNullBpmnDiagram_expectedException() {
        new BPMNConversionConnection("label", null, mock(PetrinetGraph.class), mock(DataPetriNet.class),
        		EMPTY_TRANSITION_CONVERSION_MAP, EMPTY_PLACE_CONVERSION_MAP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorDataPetriNet_withNullPetrinetGraph_expectedException() {
        new BPMNConversionConnection("label", mock(BPMNDiagram.class), null, mock(DataPetriNet.class),
        		EMPTY_TRANSITION_CONVERSION_MAP, EMPTY_PLACE_CONVERSION_MAP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorDataPetriNet_withNullDataPetriNet_expectedException() {
        new BPMNConversionConnection("label", mock(BPMNDiagram.class), mock(PetrinetGraph.class), null,
        		EMPTY_TRANSITION_CONVERSION_MAP, EMPTY_PLACE_CONVERSION_MAP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorDataPetriNet_withNullConversionMap_expectedException() {
        new BPMNConversionConnection("label", mock(BPMNDiagram.class), mock(PetrinetGraph.class),
                                     mock(DataPetriNet.class), null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorProcessTree_withNullBpmnDiagram_expectedException() {
        new BPMNConversionConnection("label", null, mock(ProcessTree.class), EMPTY_TRANSITION_CONVERSION_MAP,
        		EMPTY_PLACE_CONVERSION_MAP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorProcessTree_withNullProcessTree_expectedException() {
        ProcessTree nullTree = null;

        new BPMNConversionConnection("label", mock(BPMNDiagram.class), nullTree, EMPTY_TRANSITION_CONVERSION_MAP,
        		EMPTY_PLACE_CONVERSION_MAP);
    }


    @Test(expected = IllegalArgumentException.class)
    public void constructorProcessTree_withNullConversionMap_expectedException() {
        new BPMNConversionConnection("label", mock(BPMNDiagram.class), mock(ProcessTree.class), null, null);
    }
}
