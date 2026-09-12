package com.example.dto;

import lombok.Data;

@Data
public class LostItemClaimRequest {

    /** 领取人 */
    private String claimerName;

    /** 领取人核验信息（证件号/学工号等） */
    private String claimerVerify;

    /** 领取结论 */
    private String claimConclusion;

    /** 领取经办值班员 */
    private String operator;
}
