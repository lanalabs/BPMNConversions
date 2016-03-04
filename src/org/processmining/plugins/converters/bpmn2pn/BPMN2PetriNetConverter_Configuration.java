package org.processmining.plugins.converters.bpmn2pn;

/**
 * Configuration for conversion in {@link BPMN2PetriNetConverter_Plugin} 
 * @author dfahland
 */
public class BPMN2PetriNetConverter_Configuration {

	/**
	 * If 'true', then subprocess definitions will be linked to the calling
	 * activity. If 'false', then the subprocess definitions will be
	 * included in the resulting Petri net but as a separate fragment not
	 * connected to the main process.
	 */
	public boolean linkSubProcessToActivity = false;
	
}
