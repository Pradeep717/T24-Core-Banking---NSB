package com.temenos.t24.nsb.reports;

import java.util.ArrayList;
import java.util.List;

public class RgetDateRange {

	public List<String> getDatesInRange(String startDate, String endDate) {
		List<String> datesInRange = new ArrayList<>();

		int current = Integer.parseInt(startDate);
		int end = Integer.parseInt(endDate);

		while (current <= end) {
			datesInRange.add(String.valueOf(current));
			current = incrementDate(current);
		}

		return datesInRange;
	}

	public static int incrementDate(int date) {
		int year = date / 10000;
		int month = (date / 100) % 100;
		int day = date % 100;

		int[] daysInMonth = { 0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };

		if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) {
			daysInMonth[2] = 29; // Leap year
		}

		if (day < daysInMonth[month]) {
			return year * 10000 + month * 100 + day + 1;
		} else if (month < 12) {
			return year * 10000 + (month + 1) * 100 + 1;
		} else {
			return (year + 1) * 10000 + 101;
		}
	}

}
