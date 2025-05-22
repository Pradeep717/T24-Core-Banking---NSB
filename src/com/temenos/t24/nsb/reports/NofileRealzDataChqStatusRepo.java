package com.temenos.t24.nsb.reports;

import java.util.List;
import java.util.logging.Logger;

import com.temenos.t24.api.arrangement.accounting.Contract;
import com.temenos.t24.api.complex.eb.enquiryhook.EnquiryContext;
import com.temenos.t24.api.complex.eb.enquiryhook.FilterCriteria;
import com.temenos.t24.api.hook.system.Enquiry;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.records.teller.TellerRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

public class NofileRealzDataChqStatusRepo extends Enquiry {

	private static final Logger LOGGER = Logger.getLogger(NoFileLnPendDisbNsbRepo.class.getName());

	private final DataAccess dataAccess;

	private String branch;
	private String stockNumber;
	private String authDate;

	public NofileRealzDataChqStatusRepo() {
		this.dataAccess = new DataAccess();
	}

	public static void Info(String message) {
		String filePath = "/D:\\transaction_log.txt";
		message = message + "\n";

		try (java.io.FileWriter fileWriter = new java.io.FileWriter(filePath, true)) {
			fileWriter.write(message);
		} catch (java.io.IOException e) {
			LOGGER.severe("Error writing to log file: " + e.getMessage());
		}
	}

	@Override
	public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
		try {
			System.out.println("Inside setIds method");
			extractFilterCriteria(filterCriteria);

			processTransactions();

		} catch (Exception e) {
			LOGGER.severe("Error in setIds: " + e.getMessage());
		}

		// create list of numbers and random numbers
		List<String> result = new java.util.ArrayList<>();
		result.add("1234567890");
		result.add("0987654321");

		return result;
	}

	private void extractFilterCriteria(List<FilterCriteria> filterCriteria) {
		for (FilterCriteria criteria : filterCriteria) {
			String fieldName = criteria.getFieldname();
			String value = criteria.getValue();

			switch (fieldName) {
			case "BRANCH":
				branch = value;
				break;
			case "STOCK.NUMBER":
				stockNumber = value;
				break;
			case "AUTH.DATE":
				authDate = value;
				break;
			default:
				LOGGER.warning("Unknown field name: " + fieldName);
			}
		}
	}

	private void processTransactions() {
		Session session = new Session(this);
		String companyCode = session.getCompanyId().toString();

		String branchFilter = getBranchFilter(companyCode);
		System.out.println("Branch Filter: " + branchFilter);

		// process each transaction
		List<String> transactionIds = dataAccess.selectRecords("", "TELLER", "$HIS",
				"WITH TRANSACTION.CODE EQ 45 46 64 65 66 69 " + branchFilter);

		for (String transactionId : transactionIds) {
			try {
				processSingleTransaction(transactionId, branch, stockNumber, authDate);
			} catch (Exception e) {
				LOGGER.severe("Error processing transaction: " + e.getMessage());
			}
		}

	}

	private String getBranchFilter(String companyCode) {
		CompanyRecord company = new CompanyRecord(dataAccess.getRecord("COMPANY", companyCode));
		String companyType = company.getCompanyName().toString().toLowerCase();

		return new ReportFilterModUp2().repFilterz(companyType, companyCode);
	}

	private void processSingleTransaction(String transactionId, String branch, String stockNumber, String authDate) {
		Contract contract = new Contract();
		contract.setContractId(transactionId);

		TellerRecord tellerRecord = new TellerRecord(dataAccess.getHistoryRecord("TELLER", transactionId));
		Info("Processing transaction: " + transactionId);

	}

}
