/*
 * Copyright (C) 2009-2014 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.library;

import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.geometerplus.android.fbreader.covers.CoverManager;
import org.geometerplus.android.fbreader.tree.TreeActivity;
import org.geometerplus.android.util.ViewUtil;
import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.core.util.RationalNumber;
import org.geometerplus.zlibrary.ui.android.R;
import org.geometerplus.fbreader.book.*;
import org.geometerplus.fbreader.tree.FBTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsTree extends LibraryTree {
	private static CoverManager sharedCoverManager;

	public static ZLResource resource() {
		return ZLResource.resource("library").getResource(ROOT_STATISTICS);
	}

	// enums must have the same name as resource keys
	public static enum Types { progress, completed, reading, average, library }
	public final Types myType;
	public final String entryTitle;
	public final boolean isSelectable;
	public final static float headingSize = 1.7f;
	public final static float smallSize = 0.7f;

	// progress
	private static Book mostRecentBook;
	private static int mostRecentBookPercentRead;
	private static SpannableString mostRecentBookSummary;
	private static int mostRecentBookTimeSpent;
	private static int mostRecentBookPagesTurned;
	// completed
	private static Map<String, List<Book>> seriesCompletionMap;
	private final static Filter booksCompletedFilter = new Filter.ByCompletion(1.00f);
	private static List<Book> booksCompleted;
	private final static Filter booksReadingFilter = new Filter.ByLabel(Book.READ_LABEL);
	private static List<Book> booksReading;
	private static int totalPagesTurned;
	private static int totalTimeSpent;
	// average
	private static int averageBookTimeSpent;
	private static int averageSeriesTimeSpent;
	private static int averagePagesPerHour;
	private static int averagePagesPerSession;
	private static int averageSessionDurationSeconds;
	// library
	private final static Filter booksFavoritedFilter = new Filter.ByLabel(Book.FAVORITE_LABEL);
	private static List<Book> booksFavorited;
	private static List<Bookmark> bookmarksInLibrary;
	private static int numBooksInLibrary;
	private static int numSeriesInLibrary;
	private static int numAuthorsInLibrary;
	private static int numTagsInLibrary;

	StatisticsTree(LibraryTree parent, Types type, boolean selectable) {
		super(parent);

		myType = type;
		isSelectable = selectable;
		entryTitle = StatisticsTree.resource().getResource(type.name()).getValue();

		switch(myType) {
			case progress: {
				mostRecentBook = Collection.getRecentBook(0);
				final RationalNumber progress = mostRecentBook.getProgress();
				mostRecentBookPercentRead = (int)(progress != null ? progress.toFloat() * 100 : 0);
				final String recentBookTitle = mostRecentBook.getTitle();
				// TODO: show all authors
				mostRecentBookSummary = new SpannableString(recentBookTitle + "\n" +
						mostRecentBook.authors().get(0).DisplayName + "\n\n" +
						mostRecentBookPercentRead + "% Completed");
				mostRecentBookSummary.setSpan(new RelativeSizeSpan(1.2f), 0, recentBookTitle.length(), 0);
				final BookStatistics stats = mostRecentBook.getStatistics();
				mostRecentBookTimeSpent = stats.getTotalTimeSpent();
				mostRecentBookPagesTurned = stats.getPagesTurned();
				break;
			} case completed: {
				booksCompleted = new ArrayList<Book>();
				for (BookQuery query = new BookQuery(booksCompletedFilter, 20); ; query = query.next()) {
					final List<Book> books = Collection.books(query);
					if (books.isEmpty()) {
						break;
					}
					booksCompleted.addAll(books);
				}

				seriesCompletionMap = new HashMap<String, List<Book>>();
				for (Book book : booksCompleted) {
					final SeriesInfo seriesInfo = book.getSeriesInfo();
					if (seriesInfo == null || seriesInfo.Series == null) {
						continue;
					}
					final String title = seriesInfo.Series.getTitle();
					if(seriesCompletionMap.containsKey(title)) {
						seriesCompletionMap.get(title).add(book);
					} else {
						seriesCompletionMap.put(title, new ArrayList<Book>());
						seriesCompletionMap.get(title).add(book);
					}
				}
				final ArrayList<String> seriesToBeRemoved = new ArrayList<String>();
				for (String title : seriesCompletionMap.keySet()) {
					final Filter seriesFilter = new Filter.BySeries(new Series(title));
					// TODO: optimize this
					List<Book> allBooksInThisSeries = new ArrayList<Book>();
					for (BookQuery query = new BookQuery(seriesFilter, 20); ; query = query.next()) {
						final List<Book> books = Collection.books(query);
						if (books.isEmpty()) {
							break;
						}
						allBooksInThisSeries.addAll(books);
					}
					if (seriesCompletionMap.get(title).size() < allBooksInThisSeries.size()) {
						seriesToBeRemoved.add(title);
					}
				}
				for (String seriesToRemove : seriesToBeRemoved) {
					seriesCompletionMap.remove(seriesToRemove);
				}

				booksReading = new ArrayList<Book>();
				totalPagesTurned = 0;
				totalTimeSpent = 0;
				for (BookQuery query = new BookQuery(booksReadingFilter, 20); ; query = query.next()) {
					final List<Book> books = Collection.books(query);
					if (books.isEmpty()) {
						break;
					}
					for (Book book : books) {
						BookStatistics stats = book.getStatistics();
						totalPagesTurned += stats.getPagesTurned();
						totalTimeSpent += stats.getTotalTimeSpent();
					}
					booksReading.addAll(books);
				}
				booksFavorited = new ArrayList<Book>();
				for (BookQuery query = new BookQuery(booksFavoritedFilter, 20); ; query = query.next()) {
					final List<Book> books = Collection.books(query);
					if (books.isEmpty()) {
						break;
					}
					booksFavorited.addAll(books);
				}
				break;
			} case average: {
				averageBookTimeSpent = 0;
				averagePagesPerHour = 0;
				averagePagesPerSession = 0;
				averageSessionDurationSeconds = 0;
				for (Book book : booksReading) {
					final BookStatistics bookStatistics = book.getStatistics();
					averageBookTimeSpent += bookStatistics.getTotalTimeSpent();
					averagePagesPerHour += bookStatistics.getPagesTurned();
					averagePagesPerSession += bookStatistics.getNumberOfSessions();
					final Map<Long, Integer> sessions = bookStatistics.getProcessedSessions();
					if(sessions == null) continue;
					int currBookAverageSession = 0;
					for(int sessionLength : sessions.values()) {
						Log.d("check4", "" + sessionLength);
						currBookAverageSession += sessionLength;
					}
					averageSessionDurationSeconds += currBookAverageSession / sessions.values().size();
				}
				averageSessionDurationSeconds /= booksReading.size() < 1 ? 1 : booksReading.size();
				// order matters for this calculation; careful
				averagePagesPerSession = averagePagesPerHour / (averagePagesPerSession == 0 ? 1 : averagePagesPerSession);
				final int hours = averageBookTimeSpent / (1000 * 60 * 60);
				averagePagesPerHour /= hours < 1 ? 1 : hours;
				averageBookTimeSpent /= booksReading.size() < 1 ? 1 : booksReading.size();

				averageSeriesTimeSpent = 0;
				for (String title : seriesCompletionMap.keySet()) {
					final List<Book> booksInSeries = seriesCompletionMap.get(title);
					int currentSeriesTimeSpent = 0;
					for (Book book : booksInSeries) {
						currentSeriesTimeSpent += book.getStatistics().getTotalTimeSpent();
					}
					averageSeriesTimeSpent += currentSeriesTimeSpent / booksInSeries.size();
				}
			} case library: {
				numSeriesInLibrary = Collection.series().size();
				numAuthorsInLibrary = Collection.authors().size();
				numTagsInLibrary = Collection.tags().size();
				numBooksInLibrary = Collection.size();
				bookmarksInLibrary = new ArrayList<Bookmark>();
				for (BookmarkQuery query = new BookmarkQuery(20); ; query = query.next()) {
					final List<Bookmark> bookmarks = Collection.bookmarks(query);
					if (bookmarks.isEmpty()) {
						break;
					}
					bookmarksInLibrary.addAll(bookmarks);
				}
			} case reading: {
			} default: {
			}
		}
	}

	@Override
	public boolean hasUniqueView() { return true; }

	@Override
	public Status getOpeningStatus() {
		return isSelectable ? Status.ALWAYS_RELOAD_BEFORE_OPENING : Status.WAIT_FOR_OPEN;
	}

	@Override
	public String getName() {
		return entryTitle;
	}

	// pseudo getSummary()
	private SpannableString getSpannableSummary() {
		return mostRecentBookSummary;
	}

	// not used, see getSpannableSummary()
	@Override
	public String getSummary() { return getSpannableSummary().toString(); }

	@Override
	public Book getBook() {	return mostRecentBook; }

	@Override
	public ZLImage createCover() { return BookUtil.getCover(mostRecentBook); }

	@Override
	protected String getStringId() {
		return ROOT_STATISTICS + (mostRecentBook == null ? entryTitle : mostRecentBook.getTitle());
	}

	@Override
	public View onCreateUniqueView(View convertView, ViewGroup parent, LibraryTree tree, TreeActivity activity) {
		switch(myType) {
			case progress: {
				return createProgressStatisticsView(convertView, parent, tree, activity);
			} case completed: {
				return createCompletedStatisticsView(convertView, parent, tree, activity);
			} case average: {
				return createAverageStatisticsView(convertView, parent, tree, activity);
			} case library: {
				return createLibraryStatisticsView(convertView, parent, tree, activity);
			} case reading: {
				return createReadingStatisticsView(convertView, parent, tree, activity);
			} default: {
				return createProgressStatisticsView(convertView, parent, tree, activity);
			}
		}
	}

	private View createProgressStatisticsView(View convertView, ViewGroup parent, LibraryTree tree, TreeActivity activity) {
		final View view = (convertView != null && convertView.getId() == R.id.statistics_tree_id) ? convertView :
				LayoutInflater.from(parent.getContext()).inflate(R.layout.statistics_tree_progress, parent, false);
		final int entryHeight = parent.getMeasuredHeight() / 5 - 1;
		view.setMinimumHeight(entryHeight);

		final TextView nameView = ViewUtil.findTextView(view, R.id.statistics_tree_item_name);
		nameView.setText(tree.getName());

		final TextView summaryView = ViewUtil.findTextView(view, R.id.statistics_tree_item_summary);
		summaryView.setText(getSpannableSummary());

		int temp = mostRecentBookTimeSpent / 1000;
		final int seconds = temp % 60; temp /= 60;
		final int minutes = temp % 60; temp /= 60;
		final int hours = temp;
		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_item_right),
				headingSize, String.format("%02d:%02d:%02d", hours, minutes, seconds),
				1.0f, "\nTime Spent",
				smallSize, "\n(h:m:s)\n\n",
				headingSize, String.valueOf(mostRecentBookPagesTurned),
				1.0f, "\nPages Turned"
		);

		final DisplayMetrics metrics = new DisplayMetrics();
		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		final int coverHeight = (int)metrics.density * 90;
		final int coverWidth = coverHeight * 2 / 3;
		final ImageView coverView = ViewUtil.findImageView(view, R.id.statistics_tree_item_cover);
		if (sharedCoverManager == null) {
			Log.d("manager", "manager created from progress");
			sharedCoverManager = new CoverManager(activity, activity.ImageSynchronizer, coverWidth, coverHeight);
			view.requestLayout();
		}
		if (!sharedCoverManager.trySetCoverImage(coverView, tree)) {
			coverView.setImageResource(R.drawable.ic_list_library_book);
		}

		final ProgressBar bar = (ProgressBar)ViewUtil.findView(view, R.id.statistics_tree_item_progress);
		bar.setProgress(mostRecentBookPercentRead);

		return view;
	}

	private View createCompletedStatisticsView(View convertView, ViewGroup parent, LibraryTree tree, TreeActivity activity) {
		final View view = (convertView != null && convertView.getId() == R.id.statistics_tree_id) ? convertView :
				LayoutInflater.from(parent.getContext()).inflate(R.layout.statistics_tree_completed, parent, false);
		final int entryHeight = parent.getMeasuredHeight() / 5 - 1;
		view.setMinimumHeight(entryHeight);

		final TextView nameView = ViewUtil.findTextView(view, R.id.statistics_tree_item_name);
		nameView.setText(tree.getName());

		final int percentCompleted = (int)(RationalNumber.create(booksCompleted.size(), numBooksInLibrary).toFloat() * 100);
		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_completed_left),
			headingSize, String.valueOf(booksCompleted.size()),
			1.0f, "\nBooks\n\n",
			headingSize, String.valueOf(percentCompleted) + '%',
			1.0f, "\nOf Your Library"
		);

		int temp = totalTimeSpent / 1000 / 60;
		final int minutes = temp % 60; temp /= 60;
		final int hours = temp % 24; temp /= 24;
		final int days = temp;
		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_completed_center),
			headingSize, String.valueOf(String.format("%02d:%02d:%02d", days, hours, minutes)),
			1.0f, "\nTotal Time Spent",
			smallSize, "\n(d:h:m)\n\n"
		);

		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_completed_right),
			headingSize, String.valueOf(seriesCompletionMap.size()),
			1.0f, "\nSeries\n\n",
			headingSize, String.valueOf(totalPagesTurned),
			1.0f, "\nTotal Pages Turned"
		);

		return view;
	}

	private View createAverageStatisticsView(View convertView, ViewGroup parent, LibraryTree tree, TreeActivity activity) {
		final View view = (convertView != null && convertView.getId() == R.id.statistics_tree_id) ? convertView :
				LayoutInflater.from(parent.getContext()).inflate(R.layout.statistics_tree_average, parent, false);
		final int entryHeight = parent.getMeasuredHeight() / 5 - 1;
		view.setMinimumHeight(entryHeight);

		final TextView nameView = ViewUtil.findTextView(view, R.id.statistics_tree_item_name);
		nameView.setText(tree.getName());

		int temp = averageSessionDurationSeconds;
		int seconds = temp % 60; temp /= 60;
		int minutes = temp % 60; temp /= 60;
		int hours = temp;
		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_average_left),
			headingSize, String.format("%02d:%02d:%02d", hours, minutes, seconds),
			1.0f, "\nTime Per Session",
			smallSize, "\n(h:m:s)\n\n",
			headingSize, String.valueOf(averagePagesPerSession),
			1.0f, "\nPages Per Session"
		);

		temp = averageBookTimeSpent / 1000;
		seconds = temp % 60; temp /= 60;
		minutes = temp % 60; temp /= 60;
		hours = temp;
		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_average_center),
			headingSize, String.format("%02d:%02d:%02d", hours, minutes, seconds),
			1.0f, "\nTime Per Book",
			smallSize, "\n(h:m:s)\n\n"
		);

		temp = averageSeriesTimeSpent / 1000;
		seconds = temp % 60; temp /= 60;
		minutes = temp % 60; temp /= 60;
		hours = temp;
		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_average_right),
			headingSize, String.format("%02d:%02d:%02d", hours, minutes, seconds),
			1.0f, "\nTime Per Series",
			smallSize, "\n(h:m:s)\n\n",
			headingSize, String.valueOf(averagePagesPerHour),
			1.0f, "\nPages Per Hour"
		);

		return view;
	}

	private View createLibraryStatisticsView(View convertView, ViewGroup parent, LibraryTree tree, TreeActivity activity) {
		final View view = (convertView != null && convertView.getId() == R.id.statistics_tree_id) ? convertView :
				LayoutInflater.from(parent.getContext()).inflate(R.layout.statistics_tree_library, parent, false);
		final int entryHeight = parent.getMeasuredHeight() / 5 - 1;
		view.setMinimumHeight(entryHeight);

		final TextView nameView = ViewUtil.findTextView(view, R.id.statistics_tree_item_name);
		nameView.setText(tree.getName());

		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_left),
			headingSize, String.valueOf(numSeriesInLibrary),
			1.0f, "\nSeries\n\n",
			headingSize, String.valueOf(bookmarksInLibrary.size()),
			1.0f, "\nBookmarks"
		);

		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_centerL),
			headingSize, String.valueOf(numBooksInLibrary),
			1.0f, "\nBooks"
		);

		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_centerR),
			headingSize, String.valueOf(numAuthorsInLibrary),
			1.0f, "\nAuthors"
		);

		setTextViewContents(ViewUtil.findTextView(view, R.id.statistics_tree_right),
			headingSize, String.valueOf(numTagsInLibrary),
			1.0f, "\nTags\n\n",
			headingSize, String.valueOf(booksFavorited.size()),
			1.0f, "\nFavorites"
		);

		return view;
	}

	private View createReadingStatisticsView(View convertView, ViewGroup parent, LibraryTree tree, TreeActivity activity) {
		final View view = (convertView != null && convertView.getId() == R.id.statistics_tree_id) ? convertView :
				LayoutInflater.from(parent.getContext()).inflate(R.layout.statistics_tree_reading, parent, false);
		final int entryHeight = parent.getMeasuredHeight() / 5 - 1;
		view.setMinimumHeight(entryHeight);

		final TextView nameView = ViewUtil.findTextView(view, R.id.statistics_tree_item_name);
		nameView.setText(tree.getName());

		return view;
	}

	private void setTextViewContents(TextView tv, Object ... args) {
		final RelativeSizeSpan[] spans = new RelativeSizeSpan[args.length/2];
		final StringBuilder builder = new StringBuilder();
		for(int i = 0; i < args.length; i++) {
			if(i % 2 == 0)
				spans[i/2] = new RelativeSizeSpan((Float)args[i]);
			else
				builder.append((String)args[i]);
		}
		final SpannableString text = new SpannableString(builder);
		int left = 0, right;
		for(int i = 0; i < spans.length; i++) {
			right = left + ((String)args[2*i + 1]).length();
			text.setSpan(spans[i], left, right, 0);
			left = right;
		}
		tv.setText(text);
	}

	/*
	@Override
	private boolean onBookEvent(BookEvent event, Book book) {
		switch (event) {
			case Added: {
				return true;
			} case Updated: {
				return true;
			} case Removed: {
				return true;
			} default: {
				return super.onBookEvent(event, book);
			}
		} */


	// if statistics page contains mentioned book
	@Override
	public boolean containsBook(Book book) {
		Log.d("checkUsed", "containsbook");
		return book != null && book.equals(mostRecentBook);
	}

	// no need?
	@Override
	protected String getSortKey() {
		Log.d("checkUsed", "getsortkey");
		return ROOT_STATISTICS;
	}

	// no need?
	@Override
	public int compareTo(FBTree tree) {
		Log.d("checkUsed", "compareto");
		return -9000;
	}

	// no need?
	@Override
	public boolean equals(Object object) {
		Log.d("checkUsed", "equals");
		return false;
	}
}
