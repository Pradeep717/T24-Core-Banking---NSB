package com.temenos.t24.nsb.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.temenos.api.exceptions.T24CoreException;
import com.temenos.t24.api.arrangement.accounting.Contract;
import com.temenos.t24.api.complex.aa.contractapi.BalanceMovement;
import com.temenos.t24.api.complex.eb.enquiryhook.EnquiryContext;
import com.temenos.t24.api.complex.eb.enquiryhook.FilterCriteria;
import com.temenos.t24.api.hook.system.Enquiry;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaproductgroup.AaProductGroupRecord;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

public class NoFileDepTrailBalSummRepo extends Enquiry {
	private static final Logger LOGGER = Logger.getLogger(NoFileDepTrailBalSummRepo.class.getName());
	private static final String DELIMITER = "*";
	private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
	private static final String PRODUCT_LINE_DEPOSITS = "DEPOSITS";
	private static final String ARR_STATUS_CURRENT = "CURRENT";

	private final Contract cnt = new Contract(this);
	private final DataAccess dataAccess = new DataAccess(this);
	private final Session t24Session = new Session(this);

	private static class ProductData {
		int count = 0;
		BigDecimal totalCommitment = BigDecimal.ZERO;
		BigDecimal totalPrincipal = BigDecimal.ZERO;
		String currency = "";
	}

	@Override
	public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
		List<String> resultList = new ArrayList<>();

		try {
			// Get session and company information
			String coCode = getCompanyCode();
			String companyName = getCompanyName(coCode);
			String user = getUserId();
			String currentDateTime = getCurrentDateTime();

			// Get filter criteria
			String[] filters = getFilterCriteria(filterCriteria);
			String ftcProdGrp = filters[0];
			String ftcProd = filters[1];

			// Get deposit arrangements
			List<String> arrangements = getDepositArrangements(coCode);

			// Process arrangements and aggregate data
			Map<String, Map<String, ProductData>> productGroupMap = processArrangements(arrangements, ftcProdGrp,
					ftcProd);

			// Generate output
			generateOutput(resultList, productGroupMap, companyName, user, currentDateTime);

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Critical error in NoFileDepTrailBalSummRepo", e);
		}

		return resultList;
	}

	private String getCompanyCode() {
		try {
			return t24Session.getCompanyId().toString();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error retrieving company code", e);
			return "UNKNOWN";
		}
	}

	private String getUserId() {
		try {
			return t24Session.getUserId();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error retrieving user ID", e);
			return "UNKNOWN";
		}
	}

	private String getCurrentDateTime() {
		try {
			return LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN));
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error formatting date and time", e);
			return "UNKNOWN";
		}
	}

	private String[] getFilterCriteria(List<FilterCriteria> filterCriteria) {
		String[] filters = new String[] { "", "" };
		try {
			if (!filterCriteria.isEmpty()) {
				filters[0] = filterCriteria.get(0).getValue();
				if (filterCriteria.size() > 1) {
					filters[1] = filterCriteria.get(1).getValue();
				}
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving filter criteria", e);
		}
		return filters;
	}

	private List<String> getDepositArrangements(String coCode) {
		try {
			String selectionCriteria = String.format("WITH PRODUCT.LINE EQ %s AND ARR.STATUS EQ %s AND CO.CODE EQ %s",
					PRODUCT_LINE_DEPOSITS, ARR_STATUS_CURRENT, coCode);
			return dataAccess.selectRecords("", "AA.ARRANGEMENT", "", selectionCriteria);
		} catch (T24CoreException e) {
			LOGGER.log(Level.SEVERE, "Error retrieving AA.ARRANGEMENT records", e);
			return new ArrayList<>();
		}
	}

	private Map<String, Map<String, ProductData>> processArrangements(List<String> arrangements, String ftcProdGrp,
			String ftcProd) {
		Map<String, Map<String, ProductData>> productGroupMap = new HashMap<>();

		for (String arrId : arrangements) {
			try {
				cnt.setContractId(arrId);
				AaArrangementRecord arrRec = getArrangementRecord(arrId);

				if (arrRec != null) {
					processValidArrangement(arrRec, ftcProdGrp, ftcProd, productGroupMap);
				}
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error processing arrangement ID: " + arrId, e);
			}
		}

		return productGroupMap;
	}

	private AaArrangementRecord getArrangementRecord(String arrId) {
		try {
			return new AaArrangementRecord(dataAccess.getRecord("AA.ARRANGEMENT", arrId));
		} catch (T24CoreException e) {
			LOGGER.log(Level.WARNING, "Error retrieving AA.ARRANGEMENT record for ID: " + arrId, e);
			return null;
		}
	}

	private void processValidArrangement(AaArrangementRecord arrRec, String ftcProdGrp, String ftcProd,
			Map<String, Map<String, ProductData>> productGroupMap) {
		String recProdGroup = arrRec.getProductGroup().getValue();
		String recProduct = getProductFromArrangement(arrRec);

		if (shouldIncludeRecord(ftcProdGrp, ftcProd, recProdGroup, recProduct)) {
			String prodGrpDescription = getProductGroupDescription(recProdGroup);
			String prodDescription = getProductDescription(recProduct);
			String currency = arrRec.getCurrency().getValue();

			ProductData productData = getOrCreateProductData(productGroupMap, prodGrpDescription, prodDescription);
			updateProductData(productData, currency);
		}
	}

	private String getProductFromArrangement(AaArrangementRecord arrRec) {
		try {
			return arrRec.getProduct(0).getProduct().getValue();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving product from arrangement", e);
			return "";
		}
	}

	private boolean shouldIncludeRecord(String ftcProdGrp, String ftcProd, String recProdGroup, String recProduct) {
		if (ftcProdGrp != null && !ftcProdGrp.isEmpty() && ftcProd != null && !ftcProd.isEmpty()) {
			return ftcProdGrp.equals(recProdGroup) && ftcProd.equals(recProduct);
		} else if (ftcProdGrp != null && !ftcProdGrp.isEmpty()) {
			return ftcProdGrp.equals(recProdGroup);
		}
		return true;
	}

	private String getProductGroupDescription(String recProdGroup) {
		try {
			AaProductGroupRecord prodGrpRec = new AaProductGroupRecord(
					dataAccess.getRecord("AA.PRODUCT.GROUP", recProdGroup));
			return prodGrpRec.getDescription(0).getValue().toString();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving product group description for: " + recProdGroup, e);
			return recProdGroup;
		}
	}

	private String getProductDescription(String recProduct) {
		try {
			AaProductGroupRecord prodRec = new AaProductGroupRecord(dataAccess.getRecord("AA.PRODUCT", recProduct));
			return prodRec.getDescription(0).getValue().toString();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving product description for: " + recProduct, e);
			return recProduct;
		}
	}

	private ProductData getOrCreateProductData(Map<String, Map<String, ProductData>> productGroupMap,
			String prodGrpDescription, String prodDescription) {
		productGroupMap.computeIfAbsent(prodGrpDescription, k -> new HashMap<>());

		Map<String, ProductData> productMap = productGroupMap.get(prodGrpDescription);
		return productMap.computeIfAbsent(prodDescription, k -> new ProductData());
	}

	private void updateProductData(ProductData productData, String currency) {
		productData.count++;
		productData.totalCommitment = productData.totalCommitment.add(calculateCommitmentBalance());
		productData.totalPrincipal = productData.totalPrincipal.add(calculatePrincipalBalance());
		productData.currency = currency;
	}

	private void generateOutput(List<String> resultList, Map<String, Map<String, ProductData>> productGroupMap,
			String companyName, String user, String currentDateTime) {
		for (Map.Entry<String, Map<String, ProductData>> groupEntry : productGroupMap.entrySet()) {
			String productGroup = groupEntry.getKey();
			Map<String, ProductData> products = groupEntry.getValue();

			for (Map.Entry<String, ProductData> productEntry : products.entrySet()) {
				String product = productEntry.getKey();
				ProductData data = productEntry.getValue();

				String record = String.join(DELIMITER, productGroup, product, String.valueOf(data.count),
						data.totalCommitment.toString(), data.totalPrincipal.toString(), companyName, user,
						currentDateTime, data.currency);

				resultList.add(record);
			}
		}
	}

	private String getCompanyName(String coCode) {
		try {
			CompanyRecord company = new CompanyRecord(dataAccess.getRecord("COMPANY", coCode));
			if (company != null && !company.getCompanyName().isEmpty()) {
				return company.getCompanyName(0).getValue().toString() + " (" + coCode + ")";
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error retrieving company record: " + coCode, e);
		}
		return "Unknown Company (" + coCode + ")";
	}

	private BigDecimal calculateCommitmentBalance() {
		try {
			List<BalanceMovement> totalCommitmentList = cnt.getContractBalanceMovements("TOTCOMMITMENT", "");
			return processBalanceList(totalCommitmentList);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving commitment balances", e);
			return BigDecimal.ZERO;
		}
	}

	private BigDecimal calculatePrincipalBalance() {
		BigDecimal principalAmount = BigDecimal.ZERO;
		BigDecimal dueAmount = BigDecimal.ZERO;

		try {
			List<BalanceMovement> principleCurrentList = cnt.getContractBalanceMovements("CURACCOUNT", "");
			principalAmount = processBalanceList(principleCurrentList);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving current principal balances", e);
		}

		try {
			List<BalanceMovement> principleCurrentListDue = cnt.getContractBalanceMovements("DUEACCOUNT", "");
			dueAmount = processBalanceList(principleCurrentListDue);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving due principal balances", e);
		}

		return principalAmount.add(dueAmount);
	}

	private BigDecimal processBalanceList(List<BalanceMovement> balanceList) {
		for (BalanceMovement bl : balanceList) {
			try {
				if (bl == null || bl.getBalance() == null) {
					continue;
				}

				String balanceStr = bl.getBalance().toString();
				if (balanceStr.isEmpty()) {
					continue;
				}

				return new BigDecimal(balanceStr.replace(",", "")).abs().setScale(2, RoundingMode.HALF_UP);
			} catch (NumberFormatException e) {
				LOGGER.log(Level.FINE, "Error parsing balance number", e);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Error processing balance", e);
			}
		}
		return BigDecimal.ZERO;
	}
}