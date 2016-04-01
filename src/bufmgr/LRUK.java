package bufmgr;

import java.util.HashMap;
import diskmgr.*;
import global.*;

public class LRUK extends Replacer {
	/* File LRUK.java */

	/**
	 * private field An array to hold number of frames in the buffer pool
	 */
	private double corr_ref_period;
	private int frames[];
	private int lastref;

	/**
	 * private field number of frames used
	 */

	public HashMap<Integer, Long> lastMap = new HashMap<Integer, Long>();
	public HashMap<Integer, HashMap<Integer, Long>> historyMap = new HashMap<Integer, HashMap<Integer, Long>>();
	private int nframes;

	/**
	 * This pushes the given frame to the end of the list.
	 * 
	 * @param frameNo
	 *            the frame number
	 */
	private void update(int frameNo) {
		int index;
		index = nframes - 1;
		while (index >= 0) {
			if (frames[index] == frameNo)
				break;
			index--;
		}
		while (++index < nframes)
			frames[index - 1] = frames[index];
		frames[nframes - 1] = frameNo;
	}

	/**
	 * Calling super class the same method Initializing the frames[] with number
	 * of buffer allocated by buffer manager set number of frame used to zero
	 * 
	 * @param mgr
	 *            a BufMgr object
	 * @see BufMgr
	 * @see Replacer
	 */
	/* public methods */
	public void setBufferManager(BufMgr mgr) {
		super.setBufferManager(mgr);
		frames = new int[mgr.getNumBuffers() + 1];
		nframes = 0;

	}

	public int[] callframes() {
		return frames;
	}

	public long HIST(int page_number, int ref_no) {
		long timex;
		if (page_number != -1) {
			timex = historyMap.get(page_number).get(ref_no);
			return timex;
		}
		return 0;
	}

	public long LAST(int page_number) {
		long timex;
		timex = lastMap.get(page_number);
		return timex;
	}

	public int callpage(int frame) {
		int pagenumber;
		pagenumber = mgr.frameTable()[frame].pageNo.pid;
		return pagenumber;
	}

	// method to get Backward k-distance
	public long BACK(int pageno, int k) {
		long timex;
		if (historyMap.get(pageno) == null) {
			if (lastMap.get(pageno) == null)
				timex = 0;
			else
				timex = 0;
		} else
			timex = lastMap.get(pageno) - historyMap.get(pageno).get(k);
		;
		return timex;
	}

	/**
	 * Class constructor Initializing frames[] pinter = null.
	 */
	public LRUK(BufMgr mgrArg) {
		super(mgrArg);
		frames = null;
		lastref = 6;
		corr_ref_period = 0.2; // default is set

		// page_no=-1;
	}

	/**
	 * call super class the same method pin the page in the given frame number
	 * move the page to the end of list
	 * 
	 * @param frameNo
	 *            the frame number to pin
	 * @exception InvalidFrameNumberException
	 */
	public void pin(int frameNo) throws InvalidFrameNumberException {

		super.pin(frameNo);
		update(frameNo);
		updateLRUK(frameNo);
	}

	public void updateLRUK(int frameNo) throws InvalidFrameNumberException {
		long crp_currentpage;
		long current_time1 = System.currentTimeMillis();
		int pageID = mgr.pgno;// This is done according to the algorithm
		if ((mgr.frameTable())[frameNo].pin_count() != 0) {// only to the pages
															// which are pinned
			if (current_time1 - LAST(pageID) > corr_ref_period) {
				crp_currentpage = LAST(pageID) - HIST(pageID, 1);
				int i = lastref - 1;
				while (i >= 1) {
					long temp = historyMap.get(pageID).get(i - 1)
							+ crp_currentpage;
					historyMap.get(pageID).put(i, temp);

					i--;
				}

				historyMap.get(pageID).put(0, current_time1);// Updating hist
																// because
																// currernt_time1-last
																// > crp
				lastMap.put(pageID, current_time1);
			} else {
				lastMap.put(pageID, current_time1);// else case updating only
													// the last
			}
		}
		return;
	}

	/**
	 * Finding a free frame in the buffer pool or choosing a page to replace
	 * using LRUK policy
	 * 
	 * @return return the frame number return -1 if failed
	 */

	public int pick_victim() throws PagePinnedException,
			BufferPoolExceededException {
		int numBuffers = mgr.getNumBuffers();
		/*
		 * numbuffers for the size of the buffer manager frame to hold the frame
		 * number from frame table min is minimum time for all the k references
		 * initially assigned to current time victim is the frame number we are
		 * interested in finding pageno is to have the pagenumber and this is
		 * got from the buffer manager class as this pages are not got from the
		 * same page as they are not updated
		 */
		int frame;
		long time_current = System.currentTimeMillis();
		long mintime = time_current;
		int victim = -1;
		int page_no = mgr.pgno;
		HashMap<Integer, Long> hmap = historyMap.get(page_no);
		if (hmap != null) {
			int j;
			j = lastref - 1;
			while (j > 0) {
				long temp = historyMap.get(page_no).get(j - 1);
				historyMap.get(page_no).put(j, temp);
				j = j - 1;

			}

		} else {
			hmap = new HashMap<Integer, Long>();
			int j;
			long z;
			j = 1;
			z = 0;
			while (j < lastref) {
				hmap.put(j, z);
				j++;

			}
			historyMap.put(page_no, hmap);
		}
		historyMap.get(page_no).put(0, time_current);// Create an entry for the
														// pageno
		lastMap.put(page_no, time_current);

		if (nframes < numBuffers) {// If the buffer is still not filled
									// completely
			frame = nframes++;// assign the next frame availble
			frames[frame] = frame;
			state_bit[frame].state = Pinned;// Pin the frame
			(mgr.frameTable())[frame].pin();// increment the pin counter
			update(frame);
			//updateLRUK(frame);
			return frame;// return the frame

		}
		//if the buffer has no space for new page
		for (int i = 0; i < numBuffers; ++i) {// iterate every page in the
												// buffer.
			frame = frames[i];
			int page = (mgr.frameTable())[frame].pageNo.pid;
			if (page == -1) {// IF the page is not found in the frame table it
								// means this is coming for first time
				victim = frame;// return this frame
				break;

			} else if (time_current - LAST(page) > corr_ref_period
					&& HIST(page, lastref - 1) < mintime) {// Finding the minimum
														// value possible for
														// the replacement victim
				mintime = HIST(page, lastref - 1);// Each time keeping the minimum
				victim = frame;
												// value
			}
		}

		if (victim != -1) {// If we found an eligible frame for the new page
			if (state_bit[victim].state != Pinned) {// checking whether the
													// victim page is not pinned
				if (mgr.frameTable()[victim].pin_count() != 0) {// check the pin
																// count as well
					throw new PagePinnedException(null,
							"BUFMGR: PIN_COUNT IS NOT 0.");// if it is not zero
															// then throw
															// exception
															// accordingly
				}
				state_bit[victim].state = Pinned;// pin the victim page again
				(mgr.frameTable())[victim].pin();// increment the pin counter
				update(victim);// update the frame to the last
				try {
					Thread.sleep(2);// waiting for two seconds to make cases
									// less than correlated reference period
									// more
				} catch (Exception e) {
				}
				return victim;// return the vicitm

			}
		}
		// If we could not find the eligible page then throw an error.
		throw new BufferPoolExceededException(null, "BUFMGR: BUFFER_EXCEEDED.");
	}

	/**
	 * get the page replacement policy name
	 * 
	 * @return return the name of replacement policy used
	 */
	public String name() {
		return "LRUK";
	}

	/**
	 * print out the information of frame usage
	 */
	public void info() {
		super.info();

		System.out.print("LRUK REPLACEMENT");

		for (int i = 0; i < nframes; i++) {
			if (i % 5 == 0)
				System.out.println();
			System.out.print("\t" + frames[i]);

		}
		System.out.println();
	}

}