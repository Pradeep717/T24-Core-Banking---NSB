package com.temenos.t24.nsb.reports;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.temenos.t24.api.complex.eb.enquiryhook.EnquiryContext;
import com.temenos.t24.api.complex.eb.enquiryhook.FilterCriteria;
import com.temenos.t24.api.hook.system.Enquiry;
import com.temenos.t24.api.records.aaactivity.AaActivityRecord;
import com.temenos.t24.api.records.aaactivityhistory.AaActivityHistoryRecord;
import com.temenos.t24.api.records.aaactivityhistory.ActivityRefClass;
import com.temenos.t24.api.records.aaactivityhistory.EffectiveDateClass;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

public class NofileAaDepLoanNsb extends Enquiry {

	private static final String DELIMITER = "*";
	private static final Logger LOGGER = Logger.getLogger(NofileAaDepLoanNsb.class.getName());

	private final DataAccess dataAccess;
	private final List<String> returnList = new ArrayList<>();

	public NofileAaDepLoanNsb() {
		this.dataAccess = new DataAccess(this);
	}

	@Override
	public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
		try {
			processArrangementActivities();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in setIds", e);
		}
		return returnList;
	}

	private void processArrangementActivities() {
		Session session = new Session(this);
		String companyCode = session.getCompanyId().toString();
		String today = session.getCurrentVariable("!TODAY");

		long startTime = System.currentTimeMillis();

		// Get all arrangements for the company
		List<String> arrangementIds = dataAccess.selectRecords("", "AA.ARRANGEMENT", "",
				"WITH ARR.STATUS NE UNAUTH MATURED AND PRODUCT.LINE EQ LENDING DEPOSIT AND CO.CODE EQ " + companyCode);

		long endTime = System.currentTimeMillis();

		LOGGER.log(Level.INFO, "Processing {0} arrangements", arrangementIds.size());
		logToFile("Processing " + arrangementIds.size() + " arrangements for company code: " + companyCode);
		logToFile("Time taken to fetch arrangements: " + (endTime - startTime) + " ms");

		for (String arrangementId : arrangementIds) {
			try {
				processSingleArrangement(arrangementId, today);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error processing arrangement " + arrangementId, e);
			}
		}
	}

	private void processSingleArrangement(String arrangementId, String today) {
		// Get arrangement details
		AaArrangementRecord arrangement = new AaArrangementRecord(
				dataAccess.getRecord("AA.ARRANGEMENT", arrangementId));
		String productLine = arrangement.getProductLine().toString();
		String arrStatus = arrangement.getArrStatus().toString();
		String accountId = arrangement.getLinkedAppl().isEmpty() ? ""
				: arrangement.getLinkedAppl(0).getLinkedApplId().toString();
		String coCode = arrangement.getCoCodeRec().toString();

		// Get activity history - only check first effective date (index 0)
		AaActivityHistoryRecord activityHistory = new AaActivityHistoryRecord(
				dataAccess.getRecord("AA.ACTIVITY.HISTORY", arrangementId));

		if (activityHistory.getEffectiveDate().isEmpty()) {
			return;
		}

		EffectiveDateClass firstEffectiveDate = activityHistory.getEffectiveDate(0);
		if (!today.equals(firstEffectiveDate.getEffectiveDate().toString())) {
			return;
		}

		// Process activities for this date
		for (ActivityRefClass activityRef : firstEffectiveDate.getActivityRef()) {
			try {
				AaActivityRecord activity = new AaActivityRecord(
						dataAccess.getRecord("AA.ACTIVITY", activityRef.getActivity().getValue()));

				String inputter = extractUserId(activity.getInputter().toString());
				String authoriser = extractUserId(activity.getAuthoriser().toString());

				// Build and add the return record
				returnList.add(new ReturnListRecord(arrangementId, accountId, productLine, arrStatus, inputter,
						authoriser, coCode, firstEffectiveDate.getEffectiveDate().toString()).toString());
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error processing activity " + activityRef.getActivity().getValue(), e);
			}
		}
	}

	private String extractUserId(String fieldValue) {
		if (fieldValue == null || fieldValue.isEmpty()) {
			return "";
		}
		// Extract user ID from format like "USER_12345"
		int underscoreIndex = fieldValue.indexOf('_');
		if (underscoreIndex > 0 && underscoreIndex < fieldValue.length() - 1) {
			return fieldValue.substring(underscoreIndex + 1);
		}
		return fieldValue;
	}

	private static class ReturnListRecord {
		private final String arrangementId;
		private final String accountId;
		private final String productLine;
		private final String arrStatus;
		private final String inputter;
		private final String authoriser;
		private final String coCode;
		private final String effectiveDate;

		public ReturnListRecord(String arrangementId, String accountId, String productLine, String arrStatus,
				String inputter, String authoriser, String coCode, String effectiveDate) {
			this.arrangementId = arrangementId;
			this.accountId = accountId;
			this.productLine = productLine;
			this.arrStatus = arrStatus;
			this.inputter = inputter;
			this.authoriser = authoriser;
			this.coCode = coCode;
			this.effectiveDate = effectiveDate;
		}

		@Override
		public String toString() {
			return String.join(DELIMITER, safeString(arrangementId), safeString(accountId), safeString(productLine),
					safeString(arrStatus), safeString(inputter), safeString(authoriser), safeString(coCode),
					safeString(effectiveDate));
		}

		private String safeString(String value) {
			return value == null ? "" : value;
		}
	}

	// Debug logging method - consider removing for production
	private static void logToFile(String line) {
		String filePath = "D:\\AA.Activity3.txt";
		line = line + "\n";
		try {
			Files.write(Paths.get(filePath), line.getBytes(),
					Files.exists(Paths.get(filePath)) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error writing to file", e);
		}
	}
}