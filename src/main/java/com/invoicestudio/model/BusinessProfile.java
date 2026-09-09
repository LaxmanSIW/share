package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessProfile {
    private String name = "SHREE TRADERS";
    private String address = "12, Industrial Estate, MG Road\nMumbai – 400001, Maharashtra";
    private String gstin = "27ABCDE1234F1Z5";
    private String phone = "+91 98200 12345";
    private String email = "accounts@shreetraders.in";
    private String state = "Maharashtra";
    private String stateCode = "27";
    private String logo = ""; // data URL or file path
    private String bankName = "HDFC Bank, MG Road Branch";
    private String accountNo = "50200012345678";
    private String ifsc = "HDFC0000123";
    private String upi = "shreetraders@hdfcbank";
    private String terms = "1. Goods once sold will not be taken back.\n2. Interest @18% p.a. will be charged on overdue payments.\n3. Subject to Mumbai jurisdiction only.";

    public BusinessProfile() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }

    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }

    public String getUpi() { return upi; }
    public void setUpi(String upi) { this.upi = upi; }

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
}
