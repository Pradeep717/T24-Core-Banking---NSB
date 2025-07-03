package com.temenos.t24.nsb.reports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.temenos.t24.api.complex.eb.enquiryhook.EnquiryContext;
import com.temenos.t24.api.complex.eb.enquiryhook.FilterCriteria;
import com.temenos.t24.api.hook.system.Enquiry;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aacustomerrole.AaCustomerRoleRecord;
import com.temenos.t24.api.records.aasdbbox.AaSdbBoxRecord;
import com.temenos.t24.api.records.customer.CustomerRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

public class NoFileAaSdbBoxNsb extends Enquiry {

	private static final Logger LOGGER = Logger.getLogger(NoFileAaSdbBoxNsb.class.getName());

	private final DataAccess dataAccess;
	private final List<String> returnList = new ArrayList<>();
	private final Set<String> processedArrangements = new HashSet<>();
	private final Map<String, CustomerRecord> customerCache = new HashMap<>();
	private final Map<String, AaCustomerRoleRecord> roleCache = new HashMap<>();

	public NoFileAaSdbBoxNsb() {
		this.dataAccess = new DataAccess(this);
	}

	// Filter criteria fields
	private String account, customer, boxType, boxStatus, boxNumber, startDate;

	@Override
	public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
		try {
			extractFilterCriteria(filterCriteria);
			processArrangementIds();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in setIds", e);
		}
		return returnList;
	}

	private void extractFilterCriteria(List<FilterCriteria> filterCriteria) {
		for (FilterCriteria criteria : filterCriteria) {
			String field = criteria.getFieldname();
			String value = criteria.getValue();
			if (field == null || value == null) {
				continue;
			}

			switch (field) {
			case "ACCOUNT":
				account = value;
				break;
			case "CUSTOMER":
				customer = value;
				break;
			case "BOX.TYPE":
				boxType = value;
				break;
			case "BOX.STATUS":
				boxStatus = value;
				break;
			case "BOX.NUMBER":
				boxNumber = value;
				break;
			case "START.DATE":
				startDate = value;
				break;
			}
		}
	}

	private void processArrangementIds() {
		long startTime = System.currentTimeMillis();
		Session session = new Session(this);
		String companyId = session.getCompanyId();

		String selectionCriteria = "WITH ARR.STATUS EQ CURRENT AND PRODUCT.LINE EQ SAFE.DEPOSIT.BOX AND PROD.EFF.DATE DSND AND CO.CODE EQ "
				+ companyId;
		if (customer != null && !customer.isEmpty()) {
			selectionCriteria += " AND CUSTOMER EQ " + customer;
		}
		if (account != null && !account.isEmpty()) {
			selectionCriteria += " AND LINKED.APPL.ID EQ " + account;
		}

		List<String> arrangementIds = dataAccess.selectRecords("", "AA.ARRANGEMENT", "", selectionCriteria);

		for (String id : arrangementIds) {
			if (processedArrangements.contains(id)) {
				continue;
			}
			try {
				processArrangementId(id, companyId);
				processedArrangements.add(id);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed processing arrangement: " + id, e);
			}
		}

		long endTime = System.currentTimeMillis();
		System.out.println("Processed in " + (endTime - startTime) + " ms");
	}

	private void processArrangementId(String arrId, String companyId) throws Exception {
		AaArrangementRecord arrRec = new AaArrangementRecord(dataAccess.getRecord("AA.ARRANGEMENT", arrId));

		ArrangementDetails details = extractArrangementDetails(arrRec, arrId);
		if (details == null || (account != null && !details.account.equalsIgnoreCase(account))) {
			return;
		}

		AccountDetails accDetails = new AccountDetails();
		accDetails.startDate = arrRec.getOrigContractDate() != null ? arrRec.getOrigContractDate().toString() : null;

		ProductDetails prodDetails = new ProductDetails();
		prodDetails.productDescription = arrRec.getActiveProduct().getValue();

		CustomerDetails custDetails = new CustomerDetails();
		StringBuilder customerNames = new StringBuilder();
		StringBuilder customerRoles = new StringBuilder();

		arrRec.getCustomer().forEach(customer -> {
			String customerName = customer.getCustomer().getValue();
			String customerRole = customer.getCustomerRole().getValue();

			if (customerName != null && !customerName.isEmpty()) {
				if (customerNames.length() > 0) {
					customerNames.append(", ");
				}
				customerNames.append(customerName);
			}

			if (customerRole != null && !customerRole.isEmpty()) {
				if (customerRoles.length() > 0) {
					customerRoles.append(", ");
				}
				customerRoles.append(customerRole);
			}
		});

		custDetails.customerNames = customerNames.toString();
		custDetails.customerRoles = customerRoles.toString();

		SdbBoxDetails sdbDetails = new SdbBoxDetails();
		List<String> boxIds = dataAccess.selectRecords("", "AA.SDB.BOX", "", "WITH ARRANGEMENT.ID EQ " + arrId);
		if (!boxIds.isEmpty()) {
			AaSdbBoxRecord boxRec = new AaSdbBoxRecord(dataAccess.getRecord("AA.SDB.BOX", boxIds.get(0)));
			sdbDetails.BoxType = boxRec.getBoxType().toString();
			if (boxRec.getDescription() != null) {
				sdbDetails.BoxNumber = boxRec.getDescription().toString().replaceAll("[^0-9]", "").trim();
			}
		}

		if (boxType != null && !boxType.equalsIgnoreCase(sdbDetails.BoxType)) {
			return;
		}
		if (boxStatus != null && !boxStatus.equalsIgnoreCase(details.arrangementStatus)) {
			return;
		}
		if (boxNumber != null && (sdbDetails.BoxNumber == null || !boxNumber.equalsIgnoreCase(sdbDetails.BoxNumber))) {
			return;
		}

		if (startDate != null && accDetails.startDate != null) {
			String[] range = startDate.split("[^0-9]+");
			if (range.length == 1 && !accDetails.startDate.equals(range[0])) {
				return;
			} else if (range.length == 2
					&& (accDetails.startDate.compareTo(range[0]) < 0 || accDetails.startDate.compareTo(range[1]) > 0)) {
				return;
			}
		}

		StringBuilder record = new StringBuilder();
		record.append(details.arrangementId).append("*").append(details.productLine).append("*")
				.append(details.productGroup).append("*").append(details.product).append("*").append(details.currency)
				.append("*").append(details.arrangementStatus).append("*").append(details.branch).append("*")
				.append(details.account).append("*").append(String.join(",", details.customers)).append("*")
				.append(custDetails.customerNames).append("*").append(custDetails.customerRoles).append("*")
				.append(accDetails.startDate).append("*")
				.append(prodDetails.productDescription != null ? prodDetails.productDescription : "").append("*")
				.append(sdbDetails.BoxType != null ? sdbDetails.BoxType : "").append("*")
				.append(sdbDetails.BoxNumber != null ? sdbDetails.BoxNumber : "");

		returnList.add(record.toString());
	}

	private ArrangementDetails extractArrangementDetails(AaArrangementRecord arrRec, String arrId) {
		ArrangementDetails details = new ArrangementDetails();
		details.arrangementId = arrId;
		details.productLine = arrRec.getProductLine().toString();
		details.productGroup = arrRec.getProductGroup().toString();
		details.product = arrRec.getProduct(0).getProduct().getValue();
		details.currency = arrRec.getCurrency().toString();
		details.arrangementStatus = arrRec.getArrStatus().toString();
		details.branch = arrRec.getCoCodeRec().toString();
		details.account = arrRec.getLinkedAppl(0).getLinkedApplId().getValue();
		details.originalContractDate = arrRec.getOrigContractDate() != null ? arrRec.getOrigContractDate().toString()
				: null;

		for (int i = 0; i < arrRec.getCustomer().size(); i++) {
			details.customers.add(arrRec.getCustomer(i).getCustomer().getValue());
			details.customerRoleIds.add(arrRec.getCustomer(i).getCustomerRole().getValue());
		}
		return details;
	}

	private static class ArrangementDetails {
		String arrangementId, productLine, productGroup, product, currency, arrangementStatus, branch, account,
				originalContractDate;
		List<String> customers = new ArrayList<>();
		List<String> customerRoleIds = new ArrayList<>();
	}

	private static class AccountDetails {
		String startDate;
	}

	private static class ProductDetails {
		String productDescription;
	}

	private static class CustomerDetails {
		String customerNames, customerRoles;
	}

	private static class SdbBoxDetails {
		String BoxType, BoxNumber;
	}
}
