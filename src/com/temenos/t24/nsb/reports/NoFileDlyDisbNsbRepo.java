package com.temenos.t24.nsb.reports;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import com.temenos.api.TDate;
import com.temenos.t24.api.arrangement.accounting.Contract;
import com.temenos.t24.api.complex.aa.contractapi.BalanceMovement;
import com.temenos.t24.api.complex.eb.enquiryhook.EnquiryContext;
import com.temenos.t24.api.complex.eb.enquiryhook.FilterCriteria;
import com.temenos.t24.api.hook.system.Enquiry;
import com.temenos.t24.api.records.aaaccountdetails.AaAccountDetailsRecord;
import com.temenos.t24.api.records.aaactivity.AaActivityRecord;
import com.temenos.t24.api.records.aaactivityhistory.AaActivityHistoryRecord;
import com.temenos.t24.api.records.aaactivityhistory.ActivityRefClass;
import com.temenos.t24.api.records.aaactivityhistory.EffectiveDateClass;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangement.CustomerClass;
import com.temenos.t24.api.records.aaarrangement.LinkedApplClass;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aaarrpaymentschedule.AaArrPaymentScheduleRecord;
import com.temenos.t24.api.records.aaprddesaccount.AaPrdDesAccountRecord;
import com.temenos.t24.api.records.aaprddesinterest.AaPrdDesInterestRecord;
import com.temenos.t24.api.records.aaprddesinterest.FixedRateClass;
import com.temenos.t24.api.records.aaprddestermamount.AaPrdDesTermAmountRecord;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.records.customer.CustomerRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

public class NoFileDlyDisbNsbRepo extends Enquiry {

	private static final String DELIMITER = "*";
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final Logger LOGGER = Logger.getLogger(NoFileLnPendDisbNsbRepo.class.getName());

	private final DataAccess dataAccess;
	private final List<String> returnList = new ArrayList<>();

	// Filter criteria fields
	private String customerId;
	private String branch;
	private String productGroup;
	private String product;
	private String arrangementId;
	private String startDate;
	private String endDate;

	public NoFileDlyDisbNsbRepo() {
		this.dataAccess = new DataAccess(this);
	}

	@Override
	public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
		try {
			extractFilterCriteria(filterCriteria);

			processArrangements();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in setIds", e);
		}
		return returnList;
	}

	private void extractFilterCriteria(List<FilterCriteria> filterCriteria) {
		for (FilterCriteria criteria : filterCriteria) {
			String fieldName = criteria.getFieldname();
			String value = criteria.getValue();

			switch (fieldName) {
			case "CUSTOMER.ID":
				customerId = value;
				break;
			case "BRANCH":
				branch = value;
				break;
			case "PRODUCT.GROUP":
				productGroup = value;
				break;
			case "PRODUCT":
				product = value;
				break;
			case "ARRANGEMENT.ID":
				arrangementId = value;
				break;
			case "START.DATE":
				startDate = value;
				break;
			case "END.DATE":
				endDate = value;
				break;
			}
		}
	}

	private void processArrangements() {
		Session session = new Session(this);
		String companyCode = session.getCompanyId().toString();

		// Get date range for processing
		TDate[] dateRange = getProcessingDateRange(session);
		TDate tStartDate = dateRange[0];
		TDate tEndDate = dateRange[1];

		String co_code = session.getCompanyId().toString();

		// Get branch filter
		String branchFilter = getBranchFilter(companyCode);

		// Get current timestamp
		String currentTimestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);

		// Process each arrangement
		List<String> arrangementIds = dataAccess.selectRecords("", "AA.ARRANGEMENT", "",
				"WITH PRODUCT.LINE EQ LENDING AND ARR.STATUS EQ CURRENT AND PRODUCT.GROUP EQ AL.PERSONAL.LOAN.NSB AL.SME.LOAN.NSB HOUSING.LOAN AND LINK.DATE RG "
						+ tStartDate + " " + tEndDate + " AND CO.CODE EQ " + co_code);

		// System.out.println("Arrangement IDs: " + arrangementIds.size());
		for (String arrangementId : arrangementIds) {
			try {
				processSingleArrangement(arrangementId, tStartDate, tEndDate, currentTimestamp);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error processing arrangement " + arrangementId, e);
			}
		}
	}

	private TDate[] getProcessingDateRange(Session session) {
		String startDateValue = getDateValue(startDate, session);
		String endDateValue = getDateValue(endDate, session);

		return new TDate[] { new TDate(startDateValue), new TDate(endDateValue) };
	}

	private String getDateValue(String date, Session session) {
		if (date == null || date.trim().isEmpty()) {
			return session.getCurrentVariable("!TODAY");
		}
		return session.getCurrentVariable(date);
	}

	private String getBranchFilter(String companyCode) {
		CompanyRecord company = new CompanyRecord(dataAccess.getRecord("COMPANY", companyCode));
		String companyType = company.getCompanyName().toString().toLowerCase();

		// COMPANY TYPE IS LIKE THIS, Type: [model bank], I NEED WITHOUT [], ONLY NAME
		// AND THEN ADD ( COMPANYcODE )
		String companyTypeWithoutBracketsWithCompanyCode = companyType
				.substring(companyType.indexOf("[") + 1, companyType.indexOf("]")).trim() + "(" + companyCode + ")";
		System.out.println(companyTypeWithoutBracketsWithCompanyCode);

		return new ReportFilterModUp2().repFilterz(companyType, companyCode);
	}

	private void processSingleArrangement(String arrangementId, TDate startDate, TDate endDate,
			String currentTimestamp) {
		Contract contract = new Contract(this);
		contract.setContractId(arrangementId);

		AaArrangementRecord arrangement = new AaArrangementRecord(
				dataAccess.getRecord("AA.ARRANGEMENT", arrangementId));

		// Skip if arrangement doesn't match filter criteria
		if (!matchesFilterCriteria(arrangement)) {
			return;
		}

		// Get arrangement details
		ArrangementDetails details = extractArrangementDetails(arrangement);

		// Get balance information
		BalanceInfo balanceInfo = getBalanceInfo(contract, startDate, endDate);

		// Check if there are disbursements in the period
		DisbursementInfo disbursementInfo = getDisbursementInfo(arrangementId, startDate, endDate);

		// Process if meets criteria
		if (shouldProcessArrangement(balanceInfo, disbursementInfo)) {
			processValidArrangement(arrangementId, contract, endDate, currentTimestamp, details, balanceInfo,
					disbursementInfo);
		}
	}

	private boolean matchesFilterCriteria(AaArrangementRecord arrangement) {
		String arrangementBranch = arrangement.getCoCodeRec().toString();
		String arrangementProductGroup = arrangement.getProductGroup().toString();
		String arrangementProduct = getCurrentProduct(arrangement);
		String arrangementCustomer = getPrimaryCustomer(arrangement);

		return (customerId == null || customerId.isEmpty() || customerId.equals(arrangementCustomer))
				&& (branch == null || branch.isEmpty() || branch.equals(arrangementBranch))
				&& (productGroup == null || productGroup.isEmpty() || productGroup.equals(arrangementProductGroup))
				&& (product == null || product.isEmpty() || product.equals(arrangementProduct))
				&& (this.arrangementId == null || this.arrangementId.isEmpty()
						|| this.arrangementId.equals(arrangementId));
	}

	private String getCurrentProduct(AaArrangementRecord arrangement) {
		return arrangement.getProduct().stream().filter(p -> "CURRENT".equals(p.getProductStatus().getValue()))
				.map(p -> p.getProduct().getValue()).findFirst().orElse("");
	}

	private String getPrimaryCustomer(AaArrangementRecord arrangement) {
		if (arrangement.getCustomer().isEmpty()) {
			return "";
		}
		return arrangement.getCustomer(0).getCustomer().getValue();
	}

	private ArrangementDetails extractArrangementDetails(AaArrangementRecord arrangement) {
		ArrangementDetails details = new ArrangementDetails();

		// Customer information
		if (arrangement.getCustomer().size() > 1) {
			details.owner = formatCustomerInfo(arrangement.getCustomer(0));
			details.jointOwner = formatCustomerInfo(arrangement.getCustomer(1));
		} else if (!arrangement.getCustomer().isEmpty()) {
			details.owner = formatCustomerInfo(arrangement.getCustomer(0));
		}

		// Product information
		details.productGroup = arrangement.getProductGroup().toString();
		details.product = getCurrentProduct(arrangement);

		// Linked account
		details.linkedAccount = arrangement.getLinkedAppl().stream().map(LinkedApplClass::getLinkedApplId)
				.map(Object::toString).findFirst().orElse("");

		return details;
	}

	private String formatCustomerInfo(CustomerClass customerClass) {
		String customerId = customerClass.getCustomer().getValue();
		try {
			CustomerRecord customer = new CustomerRecord(dataAccess.getRecord("CUSTOMER", customerId));
			String customerName = customer.getShortName(0).getValue();
			return customerName + " - " + customerId;
		} catch (Exception e) {
			return customerId;
		}
	}

	private BalanceInfo getBalanceInfo(Contract contract, TDate startDate, TDate endDate) {
		BalanceInfo info = new BalanceInfo();

		try {
			info.availableBalance = getPositiveBalance(contract, "AVLACCOUNT", startDate, endDate);
		} catch (Exception e) {
			info.availableBalance = 0.0;
			LOGGER.log(Level.SEVERE, "Error getting available balance", e);
		}

		try {
			info.currentCommitment = getPositiveBalance(contract, "CURCOMMITMENT", startDate, endDate);
		} catch (Exception e) {
			info.currentCommitment = 0.0;
			LOGGER.log(Level.SEVERE, "Error getting current commitment", e);
		}

		try {
			info.totalCommitment = getPositiveBalance(contract, "TOTCOMMITMENT", startDate, endDate);
		} catch (Exception e) {
			info.totalCommitment = 0.0;
			LOGGER.log(Level.SEVERE, "Error getting total commitment", e);
		}

		return info;
	}

	private double getPositiveBalance(Contract contract, String balanceType, TDate startDate, TDate endDate) {
		List<BalanceMovement> movements = contract.getContractBalanceMovementsForPeriod(balanceType, "", startDate,
				endDate);

		if (movements.isEmpty()) {
			return 0.0;
		}

		String balanceStr = movements.get(0).getBalance().toString();

		if (balanceStr.startsWith("-")) {
			balanceStr = balanceStr.substring(1);

		}

		return Double.parseDouble(balanceStr);
	}

	private DisbursementInfo getDisbursementInfo(String arrangementId, TDate startDate, TDate endDate) {
		DisbursementInfo info = new DisbursementInfo();

		try {
			AaActivityHistoryRecord activityHistory = new AaActivityHistoryRecord(
					dataAccess.getRecord("AA.ACTIVITY.HISTORY", arrangementId));

			RgetDateRange dateRangeUtil = new RgetDateRange();
			List<String> datesInRange = dateRangeUtil.getDatesInRange(startDate.toString(), endDate.toString());

			for (EffectiveDateClass effectiveDate : activityHistory.getEffectiveDate()) {
				String effectiveDateStr = effectiveDate.getEffectiveDate().toString();

				if (datesInRange.contains(effectiveDateStr)) {
					processActivitiesForDate(effectiveDate, info);
				}
			}

			// Format total disbursed amount
			if (info.totalDisbursed > 0) {
				info.formattedTotalDisbursed = DECIMAL_FORMAT.format(new BigDecimal(info.totalDisbursed));
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error getting disbursement info", e);
		}

		return info;
	}

	private void processActivitiesForDate(EffectiveDateClass effectiveDate, DisbursementInfo info) {
		TDate currentEffectiveDate = new TDate(effectiveDate.getEffectiveDate().toString());

		for (ActivityRefClass activityRef : effectiveDate.getActivityRef()) {
			String activityId = activityRef.getActivity().getValue();

			try {
				AaActivityRecord activity = new AaActivityRecord(dataAccess.getRecord("AA.ACTIVITY", activityId));
				String description = activity.getDescription().toString();

				if (description.length() > 2
						&& "DISBURSEMENT".equalsIgnoreCase(description.substring(1, description.length() - 1))) {
					String amountStr = activityRef.getActivityAmt().toString();
					if (!amountStr.isEmpty()) {
						double amount = Double.parseDouble(amountStr);
						if (amount > 0) {
							String[] details = getDisbursementDetails(activityRef.getActivityRef().toString());

							// Skip if either inputter or authorizer is missing
							if (details == null) {
								continue;
							}

							info.disbursementCount++;
							info.totalDisbursed += amount;

							// add disbursement details
							info.disbursementAmounts += DECIMAL_FORMAT.format(new BigDecimal(amount)) + ",\n";
							info.disbursementDates += currentEffectiveDate.toString() + ",\n";
							info.disbursementInputters += details[0] + ",\n";
							info.disbursementAuthorisers += details[1] + ",\n";

							updateLastDisbursementDate(currentEffectiveDate, info);
						}
					}
				}
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error processing activity " + activityId, e);
			}
		}
	}

	private String getFormattedCompanyNameWithCode(CompanyRecord company) {
		String companyType = company.getCompanyName().toString().toLowerCase();
		String companyName = companyType.substring(companyType.indexOf("[") + 1, companyType.indexOf("]")).trim();
		if (!companyName.isEmpty()) {
			companyName = companyName.substring(0, 1).toUpperCase() + companyName.substring(1);
		}
		return companyName + " (" + company.getCoCode().toString() + ")";
	}

	private boolean isLater(TDate current, TDate last) {
		if (current.getYear() > last.getYear()) {
			return true;
		} else if (current.getYear() == last.getYear()) {
			if (current.getMonth() > last.getMonth()) {
				return true;
			} else if (current.getMonth() == last.getMonth()) {
				return current.getDay() > last.getDay();
			}
		}
		return false;
	}

	private boolean shouldProcessArrangement(BalanceInfo balanceInfo, DisbursementInfo disbursementInfo) {
		// Calculate toBeDisbursedAmount
		double toBeDisbursedAmount = calculateToBeDisbursedAmount(balanceInfo);

		// All four conditions must be true
		boolean fullyDisbursedLoans = balanceInfo.availableBalance == 0 && disbursementInfo.disbursementCount > 0
				&& disbursementInfo.totalDisbursed <= balanceInfo.totalCommitment && toBeDisbursedAmount == 0;

		boolean hasPositiveGrantedAmount1 = (balanceInfo.totalCommitment - balanceInfo.availableBalance) > 0
				&& balanceInfo.availableBalance > 0;

		boolean hasPositiveGrantedAmount2 = (balanceInfo.totalCommitment - balanceInfo.currentCommitment) > 0
				&& balanceInfo.currentCommitment > 0;

		// Only process if there are disbursements with both inputter and authorizer
		boolean partiallyDisbursedloan = (hasPositiveGrantedAmount1 || hasPositiveGrantedAmount2)
				&& disbursementInfo.disbursementCount > 0;

		// return fully or partially disbursed loans
		return fullyDisbursedLoans || partiallyDisbursedloan;

	}

	private void processValidArrangement(String arrangementId, Contract contract, TDate endDate,
			String currentTimestamp, ArrangementDetails details, BalanceInfo balanceInfo,
			DisbursementInfo disbursementInfo) {

		try {
			// Get company name with code
			Session session = new Session(this);
			String companyCode = session.getCompanyId().toString();
			CompanyRecord company = new CompanyRecord(dataAccess.getRecord("COMPANY", companyCode));
			String companyNameWithCode = getFormattedCompanyNameWithCode(company);

			// Format the last disbursed date if exists
			String formattedLastDisbursedDate = "";
			if (disbursementInfo.lastDisbursedDate != null && !disbursementInfo.lastDisbursedDate.isEmpty()) {
				try {
					// Ensure consistent date format
					formattedLastDisbursedDate = new TDate(disbursementInfo.lastDisbursedDate).toString();
				} catch (Exception e) {
					formattedLastDisbursedDate = disbursementInfo.lastDisbursedDate;
				}
			}

			// Get interest rates
			String interestRates = getInterestRates(contract, endDate);

			// Get approved amount
			String approvedAmount = balanceInfo.totalCommitment > 0
					? DECIMAL_FORMAT.format(new BigDecimal(balanceInfo.totalCommitment))
					: "0.00";

			// Calculate granted amount and to-be-disbursed amount
			double grantedAmountValue = calculateGrantedAmount(balanceInfo);
			double toBeDisbursedAmount = calculateToBeDisbursedAmount(balanceInfo);

			String formattedGrantedAmount = DECIMAL_FORMAT.format(new BigDecimal(grantedAmountValue));
			String formattedToBeDisbursed = DECIMAL_FORMAT.format(new BigDecimal(toBeDisbursedAmount));

			// Get other arrangement details
			String grantedDate = getGrantedDate(arrangementId);
			String tenure = getTenure(contract, endDate);
			String installment = getInstallmentAmount(contract, endDate);
			String purpose = getLoanPurpose(contract, endDate);

			// Build result string
			buildResultString(interestRates, approvedAmount, disbursementInfo.formattedTotalDisbursed, grantedDate,
					tenure, installment, arrangementId, details.productGroup, details.product, details.linkedAccount,
					purpose, currentTimestamp, formattedToBeDisbursed, details.owner, details.jointOwner,
					disbursementInfo.disbursementCount, formattedLastDisbursedDate,
					disbursementInfo.disbursementAmounts, disbursementInfo.disbursementDates,
					disbursementInfo.disbursementInputters, disbursementInfo.disbursementAuthorisers,
					companyNameWithCode, formattedGrantedAmount);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error processing valid arrangement " + arrangementId, e);
		}
	}

	private String getInterestRates(Contract contract, TDate endDate) {
		try {
			// Try getting interest rates from PRINCIPALINT property
			AaPrdDesInterestRecord interestRecord = new AaPrdDesInterestRecord(
					contract.getConditionForPropertyEffectiveDate("PRINCIPALINT", endDate));

			StringBuilder rates = new StringBuilder();
			for (FixedRateClass fixedRate : interestRecord.getFixedRate()) {
				try {
					// Handle different possible formats of the rate data
					String rateData = fixedRate.getEffectiveRate().toString();

					// Case 1: Direct JSON object
					if (rateData.startsWith("{")) {
						JSONObject rateObj = new JSONObject(rateData);
						double rateValue = Double.parseDouble(rateObj.optString("value", "0"));
						appendRate(rates, rateValue);
					}
					// Case 2: Might be just the value directly
					else {
						try {
							double rateValue = Double.parseDouble(rateData);
							appendRate(rates, rateValue);
						} catch (NumberFormatException e) {
							LOGGER.log(Level.WARNING, "Invalid rate format: " + rateData, e);
						}
					}
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, "Error parsing individual rate", e);
				}
			}

			// If we didn't find any rates, try alternative property
			if (rates.length() == 0) {
				return getInterestRatesFromAlternativeProperty(contract, endDate);
			}

			return rates.toString();
		} catch (Exception e) {
			return getInterestRatesFromAlternativeProperty(contract, endDate);
		}
	}

	private String getInterestRatesFromAlternativeProperty(Contract contract, TDate endDate) {
		try {
			// Try alternative property name if PRINCIPALINT fails
			AaPrdDesInterestRecord interestRecord = new AaPrdDesInterestRecord(
					contract.getConditionForPropertyEffectiveDate("INTEREST", endDate));

			StringBuilder rates = new StringBuilder();
			for (FixedRateClass fixedRate : interestRecord.getFixedRate()) {
				String rateData = fixedRate.getEffectiveRate().toString();
				if (rateData.startsWith("{")) {
					JSONObject rateObj = new JSONObject(rateData);
					double rateValue = Double.parseDouble(rateObj.optString("value", "0"));
					appendRate(rates, rateValue);
				}
			}
			return rates.length() > 0 ? rates.toString() : "0.00";
		} catch (Exception e) {
			return "0.00";
		}
	}

	private void appendRate(StringBuilder rates, double rateValue) {
		if (rates.length() > 0) {
			rates.append("|");
		}
		rates.append(DECIMAL_FORMAT.format(rateValue));
	}

	private double calculateGrantedAmount(BalanceInfo balanceInfo) {
		if (balanceInfo.availableBalance > 0) {
			return balanceInfo.totalCommitment - balanceInfo.availableBalance;
		} else if (balanceInfo.currentCommitment > 0) {
			return balanceInfo.totalCommitment - balanceInfo.currentCommitment;
		}
		return 0.0;
	}

	private double calculateToBeDisbursedAmount(BalanceInfo balanceInfo) {
		if (balanceInfo.availableBalance > 0) {
			return balanceInfo.availableBalance;
		} else if (balanceInfo.currentCommitment > 0) {
			return balanceInfo.currentCommitment;
		}
		return 0.0;
	}

	private String getGrantedDate(String arrangementId) {
		try {
			AaAccountDetailsRecord accountDetails = new AaAccountDetailsRecord(
					dataAccess.getRecord("AA.ACCOUNT.DETAILS", arrangementId));
			return accountDetails.getContractDate().get(0).toString();
		} catch (Exception e) {
			return "";
		}
	}

	private String getTenure(Contract contract, TDate endDate) {
		try {
			AaPrdDesTermAmountRecord termRecord = new AaPrdDesTermAmountRecord(
					contract.getConditionForPropertyEffectiveDate("COMMITMENT", endDate));
			return termRecord.getTerm().toString();
		} catch (Exception e) {
			return "";
		}
	}

	private String getInstallmentAmount(Contract contract, TDate endDate) {
		try {
			AaArrPaymentScheduleRecord scheduleRecord = new AaArrPaymentScheduleRecord(
					contract.getConditionForPropertyEffectiveDate("SCHEDULE", endDate));

			JSONArray paymentTypes = new JSONArray(scheduleRecord.getPaymentType().toString());
			JSONObject paymentType = paymentTypes.getJSONObject(1);
			JSONArray percentages = new JSONArray(paymentType.get("Percentage").toString());
			JSONObject percentage = percentages.getJSONObject(0);

			return percentage.get("calcAmount").toString();
		} catch (Exception e) {
			return "";
		}
	}

	private String getLoanPurpose(Contract contract, TDate endDate) {
		try {
			AaPrdDesAccountRecord accountRecord = new AaPrdDesAccountRecord(
					contract.getConditionForPropertyEffectiveDate("ACCOUNT", endDate));
			return accountRecord.getLocalRefField("L.LN.PUR.NSB").getValue();
		} catch (Exception e) {
			return "";
		}
	}

	private String[] getDisbursementDetails(String activityRef) {
		// Initialize with null to indicate missing data
		String[] details = null;

		if (activityRef == null || activityRef.isEmpty()) {
			return null;
		}

		try {
			AaArrangementActivityRecord activityRecord = new AaArrangementActivityRecord(
					dataAccess.getRecord("AA.ARRANGEMENT.ACTIVITY", activityRef));

			// Check if both inputter and authorizer exist
			String inputter = activityRecord.getInputter().toString();
			String authoriser = activityRecord.getAuthoriser().toString();

			if (!inputter.isEmpty() && !authoriser.isEmpty()) {
				details = new String[2];
				String inputterUserId = inputter.split("_")[1];
				String authoriserUserId = authoriser.split("_")[1];
				details[0] = inputterUserId;
				details[1] = authoriserUserId;
			}
			// Else return null (implicit)

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error getting disbursement details for " + activityRef, e);
		}

		return details;
	}

	private void updateLastDisbursementDate(TDate currentDate, DisbursementInfo info) {
		if (info.lastDisbursedTDate == null || isLater(currentDate, info.lastDisbursedTDate)) {
			info.lastDisbursedTDate = currentDate;
			info.lastDisbursedDate = currentDate.toString();
		}
	}

	private void buildResultString(String interestRates, String approvedAmount, String disbursedAmount,
			String grantedDate, String tenure, String installment, String arrangementId, String productGroup,
			String product, String linkedAccount, String purpose, String currentTimestamp, String toBeDisbursed,
			String owner, String jointOwner, int disbursementCount, String lastDisbursedDate,
			String disbursementAmounts, String disbursementDates, String disbursementInputters,
			String disbursementAuthorisers, String companyNameWithCode, String grantedAmount) {

		String[] parts = { interestRates, approvedAmount, disbursedAmount, grantedDate, tenure, installment,
				arrangementId, productGroup, product, linkedAccount, purpose, currentTimestamp, toBeDisbursed, owner,
				jointOwner, String.valueOf(disbursementCount), lastDisbursedDate != null ? lastDisbursedDate : "",

				disbursementAmounts, disbursementDates, disbursementInputters, disbursementAuthorisers,
				companyNameWithCode, grantedAmount };

		returnList.add(String.join(DELIMITER, parts));
	}

	// Helper classes to group related data
	private static class ArrangementDetails {
		String owner = "";
		String jointOwner = "";
		String productGroup = "";
		String product = "";
		String linkedAccount = "";
	}

	private static class BalanceInfo {
		double availableBalance = 0.0;
		double currentCommitment = 0.0;
		double totalCommitment = 0.0;
	}

	private static class DisbursementInfo {
		int disbursementCount = 0;
		double totalDisbursed = 0.0;
		String formattedTotalDisbursed = "0.00";
		String lastDisbursedDate = "";
		TDate lastDisbursedTDate = null;
		String disbursementAmounts = "";
		String disbursementDates = "";
		String disbursementInputters = "";
		String disbursementAuthorisers = "";
	}
}
