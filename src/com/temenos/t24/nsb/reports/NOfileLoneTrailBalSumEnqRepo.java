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

/**
 * Enquiry hook to retrieve and count products by product group and calculate
 * balances for each product
 *
 * @author kanakab
 */
public class NOfileLoneTrailBalSumEnqRepo extends Enquiry {
	private static final Logger LOGGER = Logger.getLogger(NOfileLoneTrailBalSumEnqRepo.class.getName());
	private Contract cnt = new Contract(this);
	private DataAccess dataAccess = new DataAccess(this);
	private Session T24session = new Session(this);

	private static class ProductData {
		int count = 0;
		BigDecimal totalCommitment = BigDecimal.ZERO;
		BigDecimal totalPrincipal = BigDecimal.ZERO;
	}

	@Override
	public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
		List<String> LTBSUM = new ArrayList<>();
		String coCode = "";
		String companyName = "";
		String user = "";
		String currentDateAndTime = "";

		try {
			try {
				coCode = T24session.getCompanyId().toString();
				user = T24session.getUserId();
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error retrieving session information", e);
				coCode = "UNKNOWN";
				user = "UNKNOWN";
			}

			try {
				DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
				LocalDateTime now = LocalDateTime.now();
				currentDateAndTime = dtf.format(now);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Error formatting date and time", e);
				currentDateAndTime = "UNKNOWN";
			}

			// Get company name
			companyName = getCompanyName(coCode);

			// Get filter criteria values safely
			String ftcProdGrp = "";
			String ftcProd = "";

			try {
				if (!filterCriteria.isEmpty()) {
					ftcProdGrp = filterCriteria.get(0).getValue();
					if (filterCriteria.size() > 1) {
						ftcProd = filterCriteria.get(1).getValue();
					}
				}
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Error retrieving filter criteria", e);
			}

			List<String> arrangements = new ArrayList<>();
			try {
				arrangements = dataAccess.selectRecords("", "AA.ARRANGEMENT", "",
						"WITH PRODUCT.LINE EQ LENDING AND ARR.STATUS EQ CURRENT EXPIRED AND CO.CODE EQ " + coCode);
			} catch (T24CoreException e) {
				LOGGER.log(Level.SEVERE, "Error retrieving AA.ARRANGEMENT records", e);
			}

			Map<String, Map<String, ProductData>> productGroupMap = new HashMap<>();

			// Process each arrangement
			for (String arrId : arrangements) {
				try {
					processArrangement(arrId, ftcProdGrp, ftcProd, productGroupMap);
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, "Error processing arrangement ID: " + arrId, e);
				}
			}

			// Generate results including counts and balance totals
			for (Map.Entry<String, Map<String, ProductData>> groupEntry : productGroupMap.entrySet()) {
				String productGroup = groupEntry.getKey();
				Map<String, ProductData> products = groupEntry.getValue();

				// Add product data
				for (Map.Entry<String, ProductData> productEntry : products.entrySet()) {
					try {
						String product = productEntry.getKey();
						ProductData data = productEntry.getValue();

						// productGroup*product*count*totalCommitment*totalPrincipal
						LTBSUM.add(productGroup + "*" + product + "*" + data.count + "*"
								+ data.totalCommitment.toString() + "*" + data.totalPrincipal.toString() + "*"
								+ companyName + "*" + user + "*" + currentDateAndTime);
					} catch (Exception e) {
						LOGGER.log(Level.WARNING, "Error formatting product data for output", e);
					}
				}
			}

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Critical error in NOfileLoneTrailBalSumEnqRepo: " + e.getMessage(), e);
		}

		return LTBSUM;
	}

	/**
	 * Get company name from company code
	 * 
	 * @param coCode Company code
	 * @return Company name with code or default value if not found
	 */
	private String getCompanyName(String coCode) {
		String companyName;
		try {
			CompanyRecord company = new CompanyRecord(dataAccess.getRecord("COMPANY", coCode));
			if (company != null && !company.getCompanyName().isEmpty()) {
				companyName = company.getCompanyName(0).getValue().toString() + " (" + coCode + ")";
			} else {
				companyName = "Unknown Company (" + coCode + ")";
				LOGGER.log(Level.WARNING, "Company record not found for CO.CODE: {0}", coCode);
			}
		} catch (Exception e) {
			companyName = "Unknown Company (" + coCode + ")";
			LOGGER.log(Level.SEVERE, "Error retrieving company record: " + coCode, e);
		}
		return companyName;
	}

	/**
	 * Process a single arrangement and update product group map
	 * 
	 * @param arrId           Arrangement ID
	 * @param ftcProdGrp      Product group filter
	 * @param ftcProd         Product filter
	 * @param productGroupMap Map to update with product data
	 */
	private void processArrangement(String arrId, String ftcProdGrp, String ftcProd,
			Map<String, Map<String, ProductData>> productGroupMap) {
		try {
			cnt.setContractId(arrId);

			// Get arrangement record
			AaArrangementRecord arrRec;
			try {
				arrRec = new AaArrangementRecord(this.dataAccess.getRecord("AA.ARRANGEMENT", arrId));
			} catch (T24CoreException e) {
				LOGGER.log(Level.WARNING, "Error retrieving AA.ARRANGEMENT record for ID: " + arrId, e);
				return;
			}

			String recProdGroup = arrRec.getProductGroup().getValue();
			String recProduct;
			try {
				recProduct = arrRec.getProduct(0).getProduct().getValue();
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Error retrieving product from arrangement: " + arrId, e);
				return;
			}

			boolean includeRecord = evaluateFilterCriteria(ftcProdGrp, ftcProd, recProdGroup, recProduct);

			// Process the record if it should be included
			if (includeRecord) {
				String prodGrpDescription;
				String prodDescription;

				try {
					AaProductGroupRecord prodGrpRec = new AaProductGroupRecord(
							dataAccess.getRecord("AA.PRODUCT.GROUP", recProdGroup));
					prodGrpDescription = prodGrpRec.getDescription(0).getValue().toString();
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Error retrieving product group description for: " + recProdGroup, e);
					prodGrpDescription = recProdGroup;
				}

				try {
					AaProductGroupRecord prodRec = new AaProductGroupRecord(
							dataAccess.getRecord("AA.PRODUCT", recProduct));
					prodDescription = prodRec.getDescription(0).getValue().toString();
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Error retrieving product description for: " + recProduct, e);
					prodDescription = recProduct;
				}

				// Add product group if not already in map
				if (!productGroupMap.containsKey(prodGrpDescription)) {
					productGroupMap.put(prodGrpDescription, new HashMap<>());
				}

				// Add product if not already in map
				Map<String, ProductData> productMap = productGroupMap.get(prodGrpDescription);
				if (!productMap.containsKey(prodDescription)) {
					productMap.put(prodDescription, new ProductData());
				}

				// Get the product data object
				ProductData productData = productMap.get(prodDescription);

				// Increment product count
				productData.count++;

				// Calculate balances and update product data
				BigDecimal commitmentAmount = calculateCommitmentBalance();
				BigDecimal totalPrincipalAmount = calculatePrincipalBalance();

				productData.totalCommitment = productData.totalCommitment.add(commitmentAmount);
				productData.totalPrincipal = productData.totalPrincipal.add(totalPrincipalAmount);
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error processing arrangement ID: " + arrId, e);
		}
	}

	/**
	 * Determine if record should be included based on filter criteria
	 */
	private boolean evaluateFilterCriteria(String ftcProdGrp, String ftcProd, String recProdGroup, String recProduct) {
		if (ftcProdGrp != null && !ftcProdGrp.isEmpty() && ftcProd != null && !ftcProd.isEmpty()) {
			// Filter by both product group and product
			return ftcProdGrp.equals(recProdGroup) && ftcProd.equals(recProduct);
		} else if (ftcProdGrp != null && !ftcProdGrp.isEmpty()) {
			// Filter by product group only
			return ftcProdGrp.equals(recProdGroup);
		} else {
			// No filters - include all records
			return true;
		}
	}

	/**
	 * Calculate commitment balance
	 * 
	 * @return Commitment amount
	 */
	private BigDecimal calculateCommitmentBalance() {
		BigDecimal commitmentAmount = BigDecimal.ZERO;
		try {
			List<BalanceMovement> totalCommitmentList = cnt.getContractBalanceMovements("TOTCOMMITMENT", "");

			for (BalanceMovement bl : totalCommitmentList) {
				try {
					if (bl == null || bl.getBalance() == null) {
						continue;
					}

					String balanceStr = bl.getBalance().toString();
					if (balanceStr.isEmpty()) {
						continue;
					}

					BigDecimal amount = new BigDecimal(balanceStr.replace(",", ""));
					commitmentAmount = amount.abs().setScale(2, RoundingMode.HALF_UP);
				} catch (NumberFormatException e) {
					LOGGER.log(Level.FINE, "Error parsing commitment balance number", e);
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Error processing commitment balance", e);
				}
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving commitment balances", e);
		}
		return commitmentAmount;
	}

	/**
	 * Calculate principal balance
	 * 
	 * @return Total principal amount
	 */
	private BigDecimal calculatePrincipalBalance() {
		BigDecimal principalAmount = BigDecimal.ZERO;
		BigDecimal dueAmount = BigDecimal.ZERO;

		try {
			// Get current principal balance
			List<BalanceMovement> principleCurrentList = cnt.getContractBalanceMovements("CURACCOUNT", "");
			principalAmount = processPrincipalBalanceList(principleCurrentList, "principal");
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving current principal balances", e);
		}

		try {
			// Get due principal balance
			List<BalanceMovement> principleCurrentListDue = cnt.getContractBalanceMovements("DUEACCOUNT", "");
			dueAmount = processPrincipalBalanceList(principleCurrentListDue, "due");
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error retrieving due principal balances", e);
		}

		return principalAmount.add(dueAmount);
	}

	/**
	 * Process a list of balance movements for principal calculations
	 * 
	 * @param balanceList The list of balance movements
	 * @param balanceType Type of balance for logging
	 * @return Processed balance amount
	 */
	private BigDecimal processPrincipalBalanceList(List<BalanceMovement> balanceList, String balanceType) {
		BigDecimal amount = BigDecimal.ZERO;

		for (BalanceMovement bl : balanceList) {
			try {
				if (bl == null || bl.getBalance() == null) {
					continue;
				}

				String balanceStr = bl.getBalance().toString();
				if (balanceStr.isEmpty()) {
					continue;
				}

				// Remove negative sign if present
				if (balanceStr.contains("-")) {
					balanceStr = balanceStr.substring(1);
				}

				BigDecimal parsedAmount = new BigDecimal(balanceStr.replace(",", ""));
				amount = parsedAmount.setScale(2, RoundingMode.HALF_UP);
				break; // Take the first valid balance
			} catch (NumberFormatException e) {
				LOGGER.log(Level.FINE, "Error parsing " + balanceType + " balance number", e);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Error processing " + balanceType + " balance", e);
			}
		}

		return amount;
	}
}
