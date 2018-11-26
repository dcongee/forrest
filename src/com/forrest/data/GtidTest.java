package com.forrest.data;

import java.util.Iterator;
import java.util.List;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.GtidSet;
import com.github.shyiko.mysql.binlog.GtidSet.Interval;
import com.github.shyiko.mysql.binlog.GtidSet.UUIDSet;

public class GtidTest {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		BinaryLogClient c = new BinaryLogClient("a", "a");
		c.setGtidSet("2772720e-6430-11e8-b673-000c29960a6c:1,e633c153-5e6e-11e8-854f-7cd30abda3ec:1-1751045427");
		System.out.println(c.getGtidSet());

		GtidSet gtid = new GtidSet(
				"2772720e-6430-11e8-b673-000c29960a6c:1,e633c153-5e6e-11e8-854f-7cd30abda3ec:1-1751045427");
		Iterator<UUIDSet> it = gtid.getUUIDSets().iterator();
		while (it.hasNext()) {
			System.out.println(it.next().getUUID());
			List<Interval> list = it.next().getIntervals();
			for (Interval i : list) {
				System.out.println(i.getStart());
				System.out.println(i.getEnd());

			}
		}
	}

}
