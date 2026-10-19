package com.kvs.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** US-04000: post a schedule's fee demands to the students of the class (identity as in studentservice). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostFeeDemandsRequest {

    @NotEmpty(message = "students must contain at least one student")
    @Size(max = 500, message = "at most 500 students per request")
    @Valid
    private List<Student> students;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Student {
        @NotNull(message = "studentId is required")
        private UUID studentId;

        @NotBlank(message = "studentUid is required")
        @Size(max = 50, message = "studentUid must be at most 50 characters")
        private String studentUid;

        @NotBlank(message = "studentName is required")
        @Size(max = 200, message = "studentName must be at most 200 characters")
        private String studentName;

        @Size(max = 10, message = "sectionName must be at most 10 characters")
        private String sectionName;

        /** authservice user id of the parent account (optional) */
        @Size(max = 100, message = "parentAccountId must be at most 100 characters")
        private String parentAccountId;
    }
}
