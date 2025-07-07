package com.temenos.t24.nsb.reports;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.temenos.api.TStructure;
import com.temenos.t24.api.complex.eb.enquiryhook.EnquiryContext;
import com.temenos.t24.api.complex.eb.enquiryhook.FilterCriteria;
import com.temenos.t24.api.hook.system.Enquiry;
import com.temenos.t24.api.records.aasdbbox.AaSdbBoxRecord;
import com.temenos.t24.api.system.DataAccess;

public class ConvAaSdbBoxNsb extends Enquiry {

	private static final Logger LOGGER = Logger.getLogger(NoFileAaSdbBoxNsb.class.getName());

	private final DataAccess dataAccess;
	private String boxType = "";
	private String boxNumber = "";

	public ConvAaSdbBoxNsb() {
		this.dataAccess = new DataAccess(this);
	}

	private static void logToFile(String line) {
//		String filePath = "/nsbt24/debug/FixAndSubsudyInt02.txt";
		String filePath = "D:\\ConvAaSdbBoxNsb.txt";
		line = line + "\n";
		try {
			Files.write(Paths.get(filePath), line.getBytes(),
					Files.exists(Paths.get(filePath)) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error writing to file", e);
		}
	}

	@Override
	public String setValue(String value, String currentId, TStructure currentRecord,
			List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {

		// LOG CURRENT ID AND CURRENT RECORD
		logToFile("Current ID: " + currentId);
		logToFile("Current Record: " + currentRecord.toString());
		System.out.println("Current ID: " + currentId);
		System.out.println("Current Record: " + currentRecord.toString());

		List<String> boxIds = dataAccess.selectRecords("", "AA.SDB.BOX", "", "WITH ARRANGEMENT.ID EQ " + currentId);
		AaSdbBoxRecord boxRec = new AaSdbBoxRecord(dataAccess.getRecord("AA.SDB.BOX", boxIds.get(0)));
		boxType = boxRec.getBoxType().getValue();
		if (boxRec.getDescription() != null) {
			boxNumber = boxRec.getDescription().toString().replaceAll("[^0-9]", "").trim();
		}

		String boxNumberAndType = boxNumber + " - " + boxType;

		return boxNumberAndType;
	}

}
