/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2011 Igalia, S.L.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.libreplan.business.test.planner.entities;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.isA;
import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.resetToNice;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.libreplan.business.BusinessGlobalNames.BUSINESS_SPRING_CONFIG_FILE;
import static org.libreplan.business.test.BusinessGlobalNames.BUSINESS_SPRING_CONFIG_TEST_FILE;
import static org.libreplan.business.test.planner.entities.DayAssignmentMatchers.haveHours;
import static org.libreplan.business.workingday.EffortDuration.hours;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import javax.annotation.Resource;

import org.easymock.IAnswer;
import org.easymock.classextension.EasyMock;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libreplan.business.IDataBootstrap;
import org.libreplan.business.calendars.entities.AvailabilityTimeLine;
import org.libreplan.business.calendars.entities.BaseCalendar;
import org.libreplan.business.calendars.entities.Capacity;
import org.libreplan.business.calendars.entities.ResourceCalendar;
import org.libreplan.business.orders.entities.HoursGroup;
import org.libreplan.business.orders.entities.Order;
import org.libreplan.business.orders.entities.OrderLine;
import org.libreplan.business.orders.entities.SchedulingDataForVersion;
import org.libreplan.business.orders.entities.TaskSource;
import org.libreplan.business.planner.entities.SpecificResourceAllocation;
import org.libreplan.business.planner.entities.Task;
import org.libreplan.business.planner.entities.TaskElement;
import org.libreplan.business.planner.limiting.entities.LimitingResourceQueueElement;
import org.libreplan.business.resources.entities.Worker;
import org.libreplan.business.scenarios.entities.OrderVersion;
import org.libreplan.business.workingday.EffortDuration;
import org.libreplan.business.workingday.IntraDayDate;
import org.libreplan.business.workingday.IntraDayDate.PartialDay;
import org.libreplan.business.workingday.ResourcesPerDay;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { BUSINESS_SPRING_CONFIG_FILE,
        BUSINESS_SPRING_CONFIG_TEST_FILE })
@Transactional
public class TaskTest {

    private static final OrderVersion mockedOrderVersion = mockOrderVersion();

    public static final OrderVersion mockOrderVersion() {
        OrderVersion result = createNiceMock(OrderVersion.class);
        replay(result);
        return result;
    }

    private BaseCalendar taskCalendar;

    private Task task;

    private HoursGroup hoursGroup;

    private ResourceCalendar workerCalendar;

    private Worker worker;

    @Resource
    private IDataBootstrap defaultAdvanceTypesBootstrapListener;

    @Before
    public void loadRequiredaData() {
        // Load data
        defaultAdvanceTypesBootstrapListener.loadRequiredData();

        // Create basic data
        hoursGroup = new HoursGroup();
        hoursGroup.setWorkingHours(3);
        Order order = new Order();
        order.useSchedulingDataFor(mockedOrderVersion);
        order.setInitDate(new Date());
        OrderLine orderLine = OrderLine.create();
        order.add(orderLine);
        order.setCalendar(stubCalendar());
        SchedulingDataForVersion version = TaskElementTest
                .mockSchedulingDataForVersion(orderLine);
        TaskSource taskSource = TaskSource.create(version, Arrays
                .asList(hoursGroup));
        task = Task.createTask(taskSource);
    }

    private BaseCalendar stubCalendar() {
        taskCalendar = createNiceMock(BaseCalendar.class);
        expect(taskCalendar.getCapacityOn(isA(PartialDay.class)))
                .andReturn(hours(8)).anyTimes();
        expect(taskCalendar.getAvailability()).andReturn(
                AvailabilityTimeLine.allValid()).anyTimes();
        replay(taskCalendar);
        return taskCalendar;
    }

    @Test
    public void taskIsASubclassOfTaskElement() {
        assertTrue(task instanceof TaskElement);
    }

    @Test
    public void taskHasHoursSpecifiedAtOrderComingFromItsHoursGroup() {
        assertThat(task.getHoursSpecifiedAtOrder(), equalTo(hoursGroup.getWorkingHours()));
    }

    @Test
    public void taskMustHaveOneHoursGroup() {
        HoursGroup hoursGroup = task.getHoursGroup();
        assertNotNull(hoursGroup);
    }

    public static Task createValidTask() {
        HoursGroup hoursGroup = new HoursGroup();
        hoursGroup.setWorkingHours(3);
        OrderLine orderLine = OrderLine.create();
        Order order = new Order();
        order.useSchedulingDataFor(mockedOrderVersion);
        order.setInitDate(new Date());
        order.add(orderLine);
        SchedulingDataForVersion version = TaskElementTest
                .mockSchedulingDataForVersion(orderLine);
        TaskSource taskSource = TaskSource.create(version, Arrays
                .asList(hoursGroup));
        return Task.createTask(taskSource);
    }

    @Test
    public void getResourceAllocationsDoesntRetrieveUnsatisfiedAllocations() {
        assertThat(task.getSatisfiedResourceAllocations().size(), equalTo(0));

        SpecificResourceAllocation unsatisfied = SpecificResourceAllocation
                .create(task);
        assertTrue("in order to be meaningful this test needs an unsatisfied "
                + "allocation", unsatisfied.isUnsatisfied());
        task.addResourceAllocation(unsatisfied);

        assertThat(task.getSatisfiedResourceAllocations().size(), equalTo(0));
        assertThat(task.getAllResourceAllocations().size(), equalTo(1));
    }

    @Test
    public void addingNoEmptyResourceAllocationAddsIt() {
        assertThat(task.getSatisfiedResourceAllocations().size(), equalTo(0));

        SpecificResourceAllocation resourceAllocation = stubResourceAllocationWithAssignedHours(
                task, 500);
        task.addResourceAllocation(resourceAllocation);
        assertThat(task.getSatisfiedResourceAllocations().size(), equalTo(1));
    }

    @Test
    public void taskRemoveResourceAllocation() {
        assertThat(task.getSatisfiedResourceAllocations().size(), equalTo(0));

        SpecificResourceAllocation resourceAllocation = stubResourceAllocationWithAssignedHours(
                task, 500);
        task.addResourceAllocation(resourceAllocation);

        assertThat(task.getSatisfiedResourceAllocations().size(), equalTo(1));

        task.removeResourceAllocation(resourceAllocation);
        assertThat(task.getSatisfiedResourceAllocations().size(), equalTo(0));
    }

    @Test
    public void aTaskWithoutAllocationsHasZeroAssignedHours() {
        assertThat(task.getAssignedHours(), equalTo(0));
    }

    @Test
    public void aTaskWithAllocationsReturnsTheSumOfItsAllocations() {
        task.addResourceAllocation(stubResourceAllocationWithAssignedHours(
                task, 5));
        task.addResourceAllocation(stubResourceAllocationWithAssignedHours(
                task, 3));
        assertThat(task.getAssignedHours(), equalTo(8));
    }

    @Test
    public void theWorkableDaysAreCalculatedBasedOnlyOnDatesNotHours() {
        task.setIntraDayStartDate(IntraDayDate.create(
                new LocalDate(2010, 1, 13), EffortDuration.hours(3)));
        task.setIntraDayEndDate(IntraDayDate.startOfDay(new LocalDate(2010, 1,
                14)));
        assertThat(task.getWorkableDays(), equalTo(1));
    }

    @Test
    public void atLeastOneWorkableDayEvenIfStartAndEndDatesAreAtTheSameDay() {
        LocalDate day = new LocalDate(2010, 1, 13);
        task.setIntraDayStartDate(IntraDayDate.create(day,
                EffortDuration.hours(3)));
        task.setIntraDayEndDate(IntraDayDate.create(day,
                EffortDuration.hours(4)));
        assertThat(task.getWorkableDays(), equalTo(1));
    }

    @Test
    public void ifTheEndIsInTheMiddleOfADayTheWholeDayIsCounted() {
        LocalDate start = new LocalDate(2010, 1, 13);
        task.setIntraDayStartDate(IntraDayDate.create(start,
                EffortDuration.hours(3)));
        task.setIntraDayEndDate(IntraDayDate.create(start.plusDays(1),
                EffortDuration.minutes(1)));
        assertThat(task.getWorkableDays(), equalTo(2));
    }

    @Test
    public void ifSomeDayIsNotWorkableIsNotCounted() {
        final LocalDate start = new LocalDate(2010, 1, 13);

        resetToNice(taskCalendar);
        expect(taskCalendar.getCapacityOn(isA(PartialDay.class))).andAnswer(
                new IAnswer<EffortDuration>() {
                    @Override
                    public EffortDuration answer() throws Throwable {
                        Object[] args = getCurrentArguments();
                        PartialDay day = (PartialDay) args[0];
                        return day.getDate().equals(start.plusDays(1)) ? hours(0)
                                : hours(8);
                    }
                }).anyTimes();
        replay(taskCalendar);

        task.setIntraDayStartDate(IntraDayDate.create(start,
                EffortDuration.hours(3)));
        task.setIntraDayEndDate(IntraDayDate.create(start.plusDays(1),
                EffortDuration.minutes(1)));
        assertThat(task.getWorkableDays(), equalTo(1));
    }

    /**
     * @param task
     * @param hours
     * @return
     */
    private SpecificResourceAllocation stubResourceAllocationWithAssignedHours(
            Task task,
            int hours) {
        SpecificResourceAllocation resourceAllocation = createNiceMock(SpecificResourceAllocation.class);
        expect(resourceAllocation.getAssignedHours()).andReturn(hours)
                .anyTimes();
        expect(resourceAllocation.getTask()).andReturn(task).anyTimes();
        expect(resourceAllocation.hasAssignments()).andReturn(true).anyTimes();
        expect(resourceAllocation.isSatisfied()).andReturn(true).anyTimes();
        resourceAllocation.detach();
        expectLastCall().anyTimes();
        replay(resourceAllocation);
        return resourceAllocation;
    }

    @Test
    public void testIsLimiting() {
        LimitingResourceQueueElement element = LimitingResourceQueueElement.create();
        Task task = createValidTask();
        SpecificResourceAllocation resourceAllocation = SpecificResourceAllocation.create(task);
        resourceAllocation.setLimitingResourceQueueElement(element);
        task.addResourceAllocation(resourceAllocation);
        assertTrue(task.getLimitingResourceAllocations().size() == 1);
    }

    @Test
    public void testIsNonLimiting() {
        Task task = createValidTask();
        SpecificResourceAllocation resourceAllocation = SpecificResourceAllocation.create(task);
        task.addResourceAllocation(resourceAllocation);
        assertTrue(task.getNonLimitingResourceAllocations().size() == 1);
    }

    @Test
    public void theoreticalHoursIsZeroIfNoResourcesAreAllocated() {
        assertThat(task.getTheoreticalCompletedHoursUntilDate(new Date()), equalTo(0));
    }

    @Test
    public void theoreticalHoursIsTotalIfDateIsLaterThanEndDate() {
        prepareTaskForTheoreticalAdvanceTesting();
        assertThat(task.getTheoreticalCompletedHoursUntilDate(task.getEndDate()), equalTo(task.getTotalHours()));

    }

    @Test
    public void theoreticalHoursIsZeroIfDateIsEarlierThanStartDate() {
        prepareTaskForTheoreticalAdvanceTesting();
        assertThat(task.getTheoreticalCompletedHoursUntilDate(task.getStartDate()), equalTo(0));

    }

    @Test
    public void theoreticalHoursWithADateWithinStartAndEndDateHead() {
        prepareTaskForTheoreticalAdvanceTesting();
        LocalDate limit = task.getStartAsLocalDate().plusDays(1);
        assertThat(task.getTheoreticalCompletedHoursUntilDate(limit.toDateTimeAtStartOfDay().toDate()), equalTo(8));
    }

    @Test
    public void theoreticalHoursWithADateWithinStartAndEndDateTail() {
        prepareTaskForTheoreticalAdvanceTesting();
        LocalDate limit = task.getEndAsLocalDate().minusDays(1);
        assertThat(task.getTheoreticalCompletedHoursUntilDate(limit.toDateTimeAtStartOfDay().toDate()), equalTo(32));
    }

    @Test
    public void theoreticalAdvancePercentageIsZeroIfNoResourcesAreAllocated() {
        assertThat(task.getTheoreticalAdvancePercentageUntilDate(new Date()), equalTo(new BigDecimal(0)));
    }

    @Test
    public void theoreticalPercentageIsOneIfDateIsLaterThanEndDate() {
        prepareTaskForTheoreticalAdvanceTesting();
        assertThat(task.getTheoreticalAdvancePercentageUntilDate(task.getEndDate()), equalTo(new BigDecimal(1)));

    }

    @Test
    public void theoreticalPercentageWithADateWithinStartAndEndDateHead() {
        prepareTaskForTheoreticalAdvanceTesting();
        LocalDate limit = task.getStartAsLocalDate().plusDays(1);
        assertThat(task.getTheoreticalAdvancePercentageUntilDate(limit.toDateTimeAtStartOfDay().toDate()),
                equalTo(new BigDecimal("0.2")));
    }

    private void prepareTaskForTheoreticalAdvanceTesting() {
        task.getHoursGroup().setWorkingHours(40);
        assertThat(task.getTotalHours(), equalTo(40));
        task.setEndDate(task.getStartAsLocalDate().plusDays(5).toDateTimeAtStartOfDay().toDate());

        SpecificResourceAllocation resourceAllocation = SpecificResourceAllocation.create(task);

        givenWorker(8);
        resourceAllocation.setResource(this.worker);
        assertTrue(resourceAllocation.getResource() != null);

        resourceAllocation.allocate(ResourcesPerDay.amount(1));
        assertThat(resourceAllocation.getAssignments().size(), equalTo(5));
        assertThat(resourceAllocation.getAssignments(), haveHours(8, 8, 8, 8, 8));

        assertThat(task.getAssignedHours(), equalTo(0));
        task.addResourceAllocation(resourceAllocation);
        assertTrue(task.getNonLimitingResourceAllocations().size() == 1);
        assertThat(task.getAssignedHours(), equalTo(40));
        assertTrue(task.getDayAssignments().size() == 5);
    }

    private void givenWorker(int hoursPerDay) {
        this.worker = createNiceMock(Worker.class);
        givenResourceCalendarAlwaysReturning(hoursPerDay);
        expect(this.worker.getCalendar()).andReturn(this.workerCalendar).anyTimes();
        replay(this.worker);
    }

    /* Taken from: SpecificResourceAllocationTest (TODO: Refactor) */
    private void givenResourceCalendarAlwaysReturning(final int hours) {
        this.workerCalendar = createNiceMock(ResourceCalendar.class);
        expect(this.workerCalendar.getCapacityOn(isA(PartialDay.class)))
        .andReturn(EffortDuration.hours(hours)).anyTimes();
        IAnswer<? extends EffortDuration> asDurationAnswer = asDurationOnAnswer(hours(hours));
        expect(
                this.workerCalendar.asDurationOn(isA(PartialDay.class),
                        isA(ResourcesPerDay.class)))
                        .andAnswer(asDurationAnswer).anyTimes();
        expect(this.workerCalendar.getCapacityWithOvertime(isA(LocalDate.class)))
        .andReturn(
                Capacity.create(hours(hours))
                .overAssignableWithoutLimit()).anyTimes();
        expect(this.workerCalendar.getAvailability()).andReturn(
                AvailabilityTimeLine.allValid()).anyTimes();
        replay(this.workerCalendar);
    }

    /* Taken from: SpecificResourceAllocationTest (TODO: Refactor) */
    private IAnswer<? extends EffortDuration> asDurationOnAnswer(
            final EffortDuration duration) {
        return new IAnswer<EffortDuration>() {

            @Override
            public EffortDuration answer() throws Throwable {
                ResourcesPerDay perDay = (ResourcesPerDay) EasyMock
                        .getCurrentArguments()[1];
                return perDay.asDurationGivenWorkingDayOf(duration);
            }
        };
    }

}