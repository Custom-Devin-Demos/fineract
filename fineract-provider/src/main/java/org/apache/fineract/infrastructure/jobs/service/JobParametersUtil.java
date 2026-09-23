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
package org.apache.fineract.infrastructure.jobs.service;

import java.util.HashSet;
import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.JobParametersIncrementer;
import org.springframework.batch.core.repository.explore.JobExplorer;

public final class JobParametersUtil {

    private JobParametersUtil() {}

    public static JobParameters toJobParameters(Map<String, JobParameter<?>> parameters) {
        return new JobParameters(new HashSet<>(parameters.values()));
    }

    /**
     * Builds the parameters of the next instance of the given job by applying the job's incrementer to the parameters
     * of its last execution.
     */
    public static JobParametersBuilder nextJobParameters(Job job, JobExplorer jobExplorer) {
        JobParametersIncrementer incrementer = job.getJobParametersIncrementer();
        if (incrementer == null) {
            return new JobParametersBuilder();
        }
        JobParameters previous = new JobParameters();
        JobInstance lastInstance = jobExplorer.getLastJobInstance(job.getName());
        if (lastInstance != null) {
            JobExecution lastExecution = jobExplorer.getLastJobExecution(lastInstance);
            if (lastExecution != null) {
                previous = lastExecution.getJobParameters();
            }
        }
        return new JobParametersBuilder(incrementer.getNext(previous));
    }
}
