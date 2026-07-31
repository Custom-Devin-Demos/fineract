/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.client.feign.fixeddeposit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * Minimal, hand-written response model for a single fixed deposit account. The OpenAPI generated model
 * {@code GetFixedDepositAccountsAccountIdResponse} does not currently expose several fields that fixed deposit
 * integration tests assert on (summary totals, pre-closure penal interest and nominal annual interest rate), so this
 * type is used to read those fields in a typed, non-deprecated way until the generated model exposes them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixedDepositAccountDataFixed {

    private Float depositAmount;
    private Float maturityAmount;
    private Integer depositPeriod;
    private Float preClosurePenalInterest;
    private Float nominalAnnualInterestRate;
    private EnumOptionData interestCalculationDaysInYearType;
    private Summary summary;
    private Map<String, Object> status;

    public Float getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(Float depositAmount) {
        this.depositAmount = depositAmount;
    }

    public Float getMaturityAmount() {
        return maturityAmount;
    }

    public void setMaturityAmount(Float maturityAmount) {
        this.maturityAmount = maturityAmount;
    }

    public Integer getDepositPeriod() {
        return depositPeriod;
    }

    public void setDepositPeriod(Integer depositPeriod) {
        this.depositPeriod = depositPeriod;
    }

    public Float getPreClosurePenalInterest() {
        return preClosurePenalInterest;
    }

    public void setPreClosurePenalInterest(Float preClosurePenalInterest) {
        this.preClosurePenalInterest = preClosurePenalInterest;
    }

    public Float getNominalAnnualInterestRate() {
        return nominalAnnualInterestRate;
    }

    public void setNominalAnnualInterestRate(Float nominalAnnualInterestRate) {
        this.nominalAnnualInterestRate = nominalAnnualInterestRate;
    }

    public EnumOptionData getInterestCalculationDaysInYearType() {
        return interestCalculationDaysInYearType;
    }

    public void setInterestCalculationDaysInYearType(EnumOptionData interestCalculationDaysInYearType) {
        this.interestCalculationDaysInYearType = interestCalculationDaysInYearType;
    }

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public Map<String, Object> getStatus() {
        return status;
    }

    public void setStatus(Map<String, Object> status) {
        this.status = status;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnumOptionData {

        private Integer id;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {

        private Float totalDeposits;
        private Float totalInterestPosted;
        private Float totalWithholdTax;
        private Float accountBalance;

        public Float getTotalDeposits() {
            return totalDeposits;
        }

        public void setTotalDeposits(Float totalDeposits) {
            this.totalDeposits = totalDeposits;
        }

        public Float getTotalInterestPosted() {
            return totalInterestPosted;
        }

        public void setTotalInterestPosted(Float totalInterestPosted) {
            this.totalInterestPosted = totalInterestPosted;
        }

        public Float getTotalWithholdTax() {
            return totalWithholdTax;
        }

        public void setTotalWithholdTax(Float totalWithholdTax) {
            this.totalWithholdTax = totalWithholdTax;
        }

        public Float getAccountBalance() {
            return accountBalance;
        }

        public void setAccountBalance(Float accountBalance) {
            this.accountBalance = accountBalance;
        }
    }
}
