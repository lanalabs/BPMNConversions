package org.processmining.plugins.converters.bpmn2pn;

/**
 * Configuration for conversion in {@link BPMN2PetriNetConverter_Plugin} 
 * @author dfahland
 */
public class BPMN2PetriNetConverter_Configuration {

	/**
	 * If 'true', then activities will be translated with life-cycle transitions
	 * start and complete visible. If false, the activities will be translated
	 * with the atomic activity execution visible.
	 */
	public boolean translateWithLifeCycleVisible = false;
	
	/**
	 * If 'true', then subprocess definitions will be linked to the calling
	 * activity. If 'false', then the subprocess definitions will be
	 * included in the resulting Petri net but as a separate fragment not
	 * connected to the main process.
	 */
	public boolean linkSubProcessToActivity = true;

	/**
	 * Provides constants for how to label elements of the generated Petri net
	 * in {@link BPMN2PetriNetConverter}
	 */
	public static enum LabelValue {
		ORIGINAL_LABEL,
		PREFIX_NONTASK_BY_BPMN_TYPE,
		PREFIX_ALL_BY_BPMN_TYPE,
		PREFIX_ALL_BY_PN_BPMN_TYPE
	}
	
	/**
	 * How to label elements of the generated Petri net, default is to prefix
	 * every element which is not a task with a prefix telling which BPMN
	 * construct it belongs to.
	 */
	public LabelValue labelNodesWith = LabelValue.PREFIX_NONTASK_BY_BPMN_TYPE;
	
	/**
	 * If 'true', then places representing sequence flows that have no
	 * user-defined label in the BPMN-model will be given generic labels (based
	 * on labels of source/target nodes).
	 */
	public boolean labelFlowPlaces = false;
	
	/**
	 * If 'true', then transitions originate from routing constructs are
	 * translated to visible transitions, otherwise these transitions will be
	 * hidden.
	 */
	public boolean makeRoutingTransitionsVisible = false;

}
