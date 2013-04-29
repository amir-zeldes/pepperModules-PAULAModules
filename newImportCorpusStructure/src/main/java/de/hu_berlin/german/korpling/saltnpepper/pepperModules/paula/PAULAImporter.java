/**
 * Copyright 2009 Humboldt University of Berlin, INRIA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.log.LogService;

import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.PepperImporter;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.PepperMapper;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.impl.PepperImporterImpl;
import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.exceptions.PAULAImporterException;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SElementId;

/**
 * This importer reads a corpus in PAULA format and maps it to a SALT corpus. 
 * The mapping of each document is done in a separate thread. 
 * 
 * @author Florian Zipser
 * @version 1.0
 *
 */
@Component(name="PAULAImporterComponent", factory="PepperImporterComponentFactory")
public class PAULAImporter extends PepperImporterImpl implements PepperImporter
{
	public PAULAImporter()
	{
		super();
		
		//setting name of module
		this.name= "PAULAImporter";
		
		//set list of formats supported by this module
		this.addSupportedFormat("paula", "1.0", null);
		
		this.getSDocumentEndings().add(ENDING_LEAF_FOLDER);
		{//just for logging: to say, that the current module has been loaded
			if (this.getLogService()!= null)
				this.getLogService().log(LogService.LOG_DEBUG,this.getName()+" is created...");
		}//just for logging: to say, that the current module has been loaded
	}
	
//===================================== start: thread number
	/**
	 * Defines the number of processes which can maximal work in parallel for importing documents.
	 * Means the number of parallel imported documents. Default value is 5.
	 */
	private Integer numOfParallelDocuments= 5;
	/**
	 * Sets the number of processes which can maximal work in parallel for importing documents.
	 * Means the number of parallel imported documents.
	 * @param numOfParallelDocuments the numOfParallelDocuments to set
	 */
	public void setNumOfParallelDocuments(Integer numOfParallelDocuments) {
		this.numOfParallelDocuments = numOfParallelDocuments;
	}

	/**
	 * Returns the number of processes which can maximal work in parallel for importing documents.
	 * Means the number of parallel imported documents.
	 * @return the numOfParallelDocuments
	 */
	public Integer getNumOfParallelDocuments() {
		return numOfParallelDocuments;
	}	
	
	public static final String PROP_NUM_OF_PARALLEL_DOCUMENTS="paulaImporter.numOfParallelDocuments";
//===================================== start: thread number
	
// ========================== start: flagging for parallel running	
	/**
	 * If true, PAULAImporter imports documents in parallel.
	 */
	private Boolean RUN_IN_PARALLEL= true;
	/**
	 * @param rUN_IN_PARALLEL the rUN_IN_PARALLEL to set
	 */
	public void setRUN_IN_PARALLEL(Boolean rUN_IN_PARALLEL) {
		RUN_IN_PARALLEL = rUN_IN_PARALLEL;
	}

	/**
	 * @return the RUN_IN_PARALLEL
	 */
	public Boolean getRUN_IN_PARALLEL() {
		return RUN_IN_PARALLEL;
	}
	
	/**
	 * Identifier of properties which contains the maximal number of parallel processed documents. 
	 */
	public static final String PROP_RUN_IN_PARALLEL="paulaImporter.runInParallel";
// ========================== end: flagging for parallel running
	/**
	 * a property representation of a property file
	 */
	protected Properties props= null;

//// ========================== start: extract corpus-path	
	/**
	 * Stores the endings which are used for paula-files
	 */
	private String[] PAULA_FILE_ENDINGS= {"xml","paula"};
// ========================== end: extract corpus-path
	/**
	 * Extracts properties out of given special parameters.
	 */
	//TODO create PAULAProperties
	private void exctractProperties()
	{
		if (this.getSpecialParams()!= null)
		{//check if flag for running in parallel is set
			File propFile= new File(this.getSpecialParams().toFileString());
			this.props= new Properties();
			try{
				this.props.load(new FileInputStream(propFile));
			}catch (Exception e)
			{throw new PAULAImporterException("Cannot find input file for properties: "+propFile+"\n nested exception: "+ e.getMessage());}
			if (this.props.containsKey(PROP_RUN_IN_PARALLEL))
			{
				try {
					Boolean val= new Boolean(this.props.getProperty(PROP_RUN_IN_PARALLEL));
					this.setRUN_IN_PARALLEL(val);
				} catch (Exception e) 
				{
					if (this.getLogService()!= null)
						this.getLogService().log(LogService.LOG_WARNING, "Cannot set correct property value of property "+PROP_RUN_IN_PARALLEL+" to "+this.getName()+", because of the value is not castable to Boolean. A correct value can contain 'true' or 'false'.");
				}
			}
			else if (this.props.containsKey(PROP_NUM_OF_PARALLEL_DOCUMENTS))
			{
				try {
					Integer val= new Integer(this.props.getProperty(PROP_NUM_OF_PARALLEL_DOCUMENTS));
					if (val > 0)
						this.setNumOfParallelDocuments(val);
				} catch (Exception e) 
				{
					if (this.getLogService()!= null)
						this.getLogService().log(LogService.LOG_WARNING, "Cannot set correct property value of property "+PROP_NUM_OF_PARALLEL_DOCUMENTS+" to "+this.getName()+", because of the value is not castable to Integer. A correct value must be a positiv, whole number (>0).");
				}
			}
		}//check if flag for running in parallel is set
	}
	
	/**
	 * Creates a mapper of type {@link PAULA2SaltMapper}.
	 * {@inheritDoc PepperModule#createPepperMapper(SElementId)}
	 */
	@Override
	public PepperMapper createPepperMapper(SElementId sElementId)
	{
		PAULA2SaltMapper mapper= new PAULA2SaltMapper();
		mapper.setPAULA_FILE_ENDINGS(PAULA_FILE_ENDINGS);
		return(mapper);
	}
}
