package com.temenos.t24.nsb.reports;

import java.util.List;

import com.temenos.t24.api.complex.eb.enquiryhook.EnquiryContext;
import com.temenos.t24.api.complex.eb.enquiryhook.FilterCriteria;
import com.temenos.t24.api.hook.system.Enquiry;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

public class ConvOutgoingCheckNsb extends Enquiry {

	private final DataAccess dataAccess;

	public ConvOutgoingCheckNsb() {
		this.dataAccess = new DataAccess(this);
	}

	@Override
	public List<FilterCriteria> setFilterCriteria(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
		// TODO Auto-generated method stub
		Session session = new Session(this);

		String co_code = session.getCompanyId().toString();
		CompanyRecord companyRecord = new CompanyRecord(dataAccess.getRecord("COMPANY", co_code));
		String mnemonic = companyRecord.getMnemonic().toString();

		FilterCriteria filterCriteria1 = new FilterCriteria();
		filterCriteria1.setFieldname("CompanyID");
		filterCriteria1.setOperand("EQ");
		filterCriteria1.setValue(mnemonic);

		filterCriteria.add(filterCriteria1);
		return filterCriteria;
	}

}
