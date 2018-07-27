package pair.distribution.app.helpers;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import pair.distribution.app.helpers.DayPairsHelper;
import pair.distribution.app.persistence.mongodb.TrelloPairsRepository;
import pair.distribution.app.trello.entities.Company;
import pair.distribution.app.trello.entities.DayPairs;
import pair.distribution.app.trello.entities.DevPairCombinations;
import pair.distribution.app.trello.entities.Developer;
import pair.distribution.app.trello.entities.Pair;
import pair.distribution.app.trello.entities.PairCombinations;

public class DayPairsHelperTest {

	private DayPairsHelper subject;
	private TrelloPairsRepository trelloPairsRepository;


	@Before
	public void setUp() {
		trelloPairsRepository = mock(TrelloPairsRepository.class);
		subject = new DayPairsHelper(trelloPairsRepository);
	}
	
	
	@Test(expected=RuntimeException.class)
	public void testUpdateDataBaseWithTrelloContentWithException() {
		List<DayPairs> pairsList = getPairsListFromDevs(getStandardDevs());
		when(trelloPairsRepository.findByDate(pairsList.get(2).getDate())).thenReturn(pairsList);
		
		subject.updateDataBaseWithTrelloContent(pairsList);
	}
	
	@Test
	public void testUpdateDataBaseWithTrelloContent() {
		List<DayPairs> pairsList = getPairsListFromDevs(getStandardDevs());
		DayPairs oldPairs = new DayPairs();
		oldPairs.setDate(pairsList.get(0).getDate());
		oldPairs.addPair("oldTrack", new Pair());
		when(trelloPairsRepository.findByDate(pairsList.get(0).getDate())).thenReturn(Arrays.asList(oldPairs));
		when(trelloPairsRepository.findByDate(pairsList.get(1).getDate())).thenReturn(Arrays.asList());
		
		subject.updateDataBaseWithTrelloContent(pairsList);
		
		verify(trelloPairsRepository, atLeast(1)).save(pairsList.get(0));
		verify(trelloPairsRepository, atLeast(1)).save(pairsList.get(1));
	}
	
	@Test
	public void testBuildPairsWeightFromPastPairing() {
		PairCombinations pairs = getPairsList();
		List<Developer> devs = getStandardDevs();
		
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")))), is(2));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev3")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev4")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4")))), is(2));
	}
	
	@Test
	public void testAdaptPairsWeightForNewDevelopers() {
		
		Developer developer1 = new Developer("dev1");
		developer1.setNew(true);
		Developer developer2 = new Developer("dev2");
		developer2.setNew(true);
		Developer developer3 = new Developer("dev3");
		Developer developer4 = new Developer("dev4");
		List<Developer> devs = Arrays.asList(developer1, developer2, developer3, developer4);
		PairCombinations pairs = new DevPairCombinations(getPairsListFromDevs(devs));
		
		
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		subject.adaptPairsWeight(pairsWeight, devs);
		
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")))), is(102));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev3")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev4")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4")))), is(2));
	}
	
	@Test
	public void testGenerateNewDayPairs() {
		PairCombinations pairs = getPairsList();
		List<Developer> devs = getStandardDevs();
		List<String> tracks = Arrays.asList("track1", "track2", "track3");
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		
		DayPairs dayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		
		assertThat(dayPairs.getTracks().size(), is(2));
		assertThat(dayPairs.getTracks(), contains("track1", "track2"));
		assertThat(dayPairs.getPairByTrack("track1"), is(not(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2"))))));
		assertThat(dayPairs.getPairByTrack("track2"), is(not(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4"))))));
	}
	
	@Test
	public void testGenerateNewDayPairsWithSmallestWeight() {
		PairCombinations pairs = getLongPairsList();
		List<Developer> devs = Arrays.asList(new Developer("dev1"), new Developer("dev2"), new Developer("dev3"), new Developer("dev4"), new Developer("dev5"), new Developer("dev6"));
		List<String> tracks = Arrays.asList("track1", "track2", "track3");
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		
		DayPairs dayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		
		assertThat(dayPairs.getTracks().size(), is(3));
		assertThat(dayPairs.getTracks(), contains("track1", "track2", "track3"));
		System.out.println(dayPairs.getPairs());
		assertThat(dayPairs.hasPair(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev6")))), is(true));
		assertThat(dayPairs.hasPair(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev2")))), is(true));
		assertThat(dayPairs.hasPair(new Pair(Arrays.asList(new Developer("dev5"), new Developer("dev4")))), is(true));
	}
	
	@Test
	public void testGenerateNewDayPairsSoloRequired() {
		PairCombinations pairs = getPairsList();
		List<Developer> devs = Arrays.asList(new Developer("dev1"), new Developer("dev2"), new Developer("dev3"));
		List<String> tracks = Arrays.asList("track1", "track2", "track3");
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		
		DayPairs dayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		
		assertThat(dayPairs.getTracks().size(), is(2));
		assertThat(dayPairs.getTracks(), contains("track1", "track2"));
		assertThat(dayPairs.getPairByTrack("track1"), is((new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2"))))));
	}

	@Test
	public void testGenerateNewDayPairsNoOldDevAvailableTrackHistoryAvailable() {
		PairCombinations pairs = getPairsListWithNoHistoryForLastThreeDays(getStandardDevs());
		List<Developer> devs = Arrays.asList(new Developer("dev5"), new Developer("dev6"), new Developer("dev3"), new Developer("dev4"));
		List<String> tracks = Arrays.asList("track1", "track2", "track3");
		
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		subject.buildDevTracksWeightFromPastPairing(pairs, devs);
		
		DayPairs dayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		
		assertThat(dayPairs.getTracks().size(), is(2));
		assertArrayEquals(dayPairs.getTracks().toArray(new String[0]), new String[] {"track1", "track2"});
		assertThat(dayPairs.getPairByTrack("track1"), is(new Pair(Arrays.asList(new Developer("dev5"), new Developer("dev6")))));
	}

	@Test
	public void testGenerateNewDayPairsOnlyOldDevAvailableForStory() {
		PairCombinations pairs = getLongPairsList();
		List<Developer> devs = Arrays.asList(new Developer("dev1"), new Developer("dev3"), new Developer("dev4"));
		List<String> tracks = Arrays.asList("track1", "track2", "track3");
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		
		DayPairs dayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		
		assertThat(dayPairs.getTracks().size(), is(2));
		assertThat(dayPairs.getTracks(), contains("track1", "track2"));
		System.out.println(dayPairs.getPairs());
		assertThat(dayPairs.hasPair(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(true));
		assertThat(dayPairs.hasPair(new Pair(Arrays.asList(new Developer("dev3")))), is(true));
	}
		
	@Test
	public void testGenerateNewDayPairsNewDevsAvailable() {
		
		PairCombinations pairs = getPairsList();
		Developer developer2 = new Developer("dev2");
		developer2.setNew(true);
		Developer developer3 = new Developer("dev3");
		developer3.setNew(true);
		List<Developer> devs = Arrays.asList(new Developer("dev1"), developer2, developer3, new Developer("dev4"));
		List<String> tracks = Arrays.asList("track1", "track2", "track3");
		
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		subject.adaptPairsWeight(pairsWeight, devs);
		
		DayPairs todayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		
		assertThat(todayPairs.getPairByTrack("track1"), is(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")))));
		assertThat(todayPairs.getPairByTrack("track2"), is(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4")))));
	}
	
	@Test
	public void testGenerateNewDayPairsNewDeveloperSolo() {
		Developer developer1 = new Developer("dev1");
		developer1.setNew(true);
		Developer developer2 = new Developer("dev2");
		Developer developer3 = new Developer("dev3");
		DayPairs dayPairs = new DayPairs();
		dayPairs.addPair("track1", new Pair(Arrays.asList(developer1)));
		dayPairs.addPair("track2", new Pair(Arrays.asList(developer2, developer3)));
		dayPairs.setDate(getPastDate(1));
		DevPairCombinations pairs = new DevPairCombinations(Arrays.asList(dayPairs));
		List<Developer> devs = Arrays.asList(developer1, developer2, developer3);
		List<String> tracks = Arrays.asList("track1", "track2");
		
		
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		subject.adaptPairsWeight(pairsWeight, devs);
		
		DayPairs todayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		subject.rotateSoloPairIfAny(todayPairs, pairs, pairsWeight);
		
		assertThat(todayPairs.getPairByTrack("track1"), is(not(new Pair(Arrays.asList(new Developer("dev1"))))));
		assertThat(todayPairs.getPairByTrack("track1"), is(not(new Pair(Arrays.asList(developer2, developer3)))));
	}
	
	@Test
	public void testGenerateNewDayPairsNoPastState() {
		DevPairCombinations pairs = new DevPairCombinations(new ArrayList<>());
		List<Developer> devs = getStandardDevs();
		List<String> tracks = Arrays.asList("track1", "track2", "track3");
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		
		DayPairs dayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		
		assertThat(dayPairs.getTracks().size(), is(2));
		assertThat(dayPairs.getTracks(), contains("track1", "track2"));
	}
	
	@Test
	public void testGenerateNewDayPairsNoPastStateAndRotationNeeded() {
		DevPairCombinations pairs = new DevPairCombinations(new ArrayList<>());
		List<Developer> devs = getStandardDevs();
		List<String> tracks = Arrays.asList("track1", "track2", "track3");
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		
		DayPairs dayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, true, getStandardCompanies());
		
		assertThat(dayPairs.getTracks().size(), is(2));
		assertThat(dayPairs.getTracks(), contains("track1", "track2"));
	}

	@Test
	public void testGenerateNewDayPairsCompanyTrack() {
		PairCombinations pairs = getLongPairsList();
		Developer companyBarDev = new Developer("dev1");
		companyBarDev.setCompany(new Company("BAR"));
		Developer companyBarDev2 = new Developer("dev2");
		companyBarDev2.setCompany(new Company("BAR"));
		List<Developer> devs = Arrays.asList(companyBarDev, companyBarDev2, new Developer("dev3"), new Developer("dev4"), new Developer("dev5"), new Developer("dev6"));
		List<String> tracks = Arrays.asList("bar-track1", "track2", "track3");
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(pairs, devs);
		
		DayPairs dayPairs = subject.generateNewDayPairs(tracks, devs, pairs, pairsWeight, false, getStandardCompanies());
		
		assertThat(dayPairs.getTracks().size(), is(3));
		assertThat(dayPairs.getTracks(), hasItems("bar-track1", "track2", "track3"));
		assertThat(dayPairs.hasPair(new Pair(Arrays.asList(companyBarDev, companyBarDev2))), is(true));
	}
	
	private PairCombinations getPairsList() {
		List<Developer> devs = getStandardDevs();
		return new DevPairCombinations(getPairsListFromDevs(devs));
	}

	private List<Company> getStandardCompanies(){
		return Arrays.asList(new Company("FOO"), new Company("BAR"));
	}

	private List<Developer> getStandardDevs() {
		return Arrays.asList(new Developer("dev1"), new Developer("dev2"), new Developer("dev3"), new Developer("dev4"));
	}
	
	private PairCombinations getLongPairsList() {
		List<Developer> devs = Arrays.asList(new Developer("dev1"), new Developer("dev2"), new Developer("dev3"), new Developer("dev4"), new Developer("dev5"), new Developer("dev6"));
		return new DevPairCombinations(getPairsListWithLongestDevFromDevs(devs));
	}
	
	private List<DayPairs> getPairsListFromDevs(List<Developer> devs) {
		ArrayList<DayPairs> result = new ArrayList<DayPairs>();
		for(int i = 1; i < 3 ; i++){
			DayPairs pairs = new DayPairs();
			pairs.setDate(getPastDate(i));
			Pair pairTrack1 = new Pair(Arrays.asList(devs.get(0), devs.get(1)));
			pairTrack1.setTrack("track1");
			pairs.addPair("track1", pairTrack1);
			Pair pairTrack2 = new Pair(Arrays.asList(devs.get(2), devs.get(3)));
			pairTrack2.setTrack("track2");
			pairs.addPair("track2", pairTrack2);
			result.add(pairs);
		}
		DayPairs pairs = new DayPairs();
		pairs.setDate(getPastDate(3));
		Pair pairTrack1 = new Pair(Arrays.asList(devs.get(0), devs.get(3)));
		pairTrack1.setTrack("track1");
		pairs.addPair("track1", pairTrack1);
		Pair pairTrack2 = new Pair(Arrays.asList(devs.get(2), devs.get(1)));
		pairTrack2.setTrack("track2");
		pairs.addPair("track2", pairTrack2);
		result.add(pairs);
		
		return result;
	}
	
	private List<DayPairs> getPairsListWithLongestDevFromDevs(List<Developer> devs) {
		ArrayList<DayPairs> result = new ArrayList<DayPairs>();
		for(int i = 1; i < 3 ; i++){
			DayPairs pairs = new DayPairs();
			pairs.setDate(getPastDate(i));
			pairs.addPair("track1", new Pair(Arrays.asList(devs.get(0), devs.get(3))));
			pairs.addPair("track2", new Pair(Arrays.asList(devs.get(2), devs.get(5))));
			pairs.addPair("track3", new Pair(Arrays.asList(devs.get(4), devs.get(1))));
			result.add(pairs);
		}
		
		for(int i = 3; i < 5 ; i++){
			DayPairs pairs = new DayPairs();
			pairs.setDate(getPastDate(i));
			pairs.addPair("track1", new Pair(Arrays.asList(devs.get(0), devs.get(1))));
			pairs.addPair("track2", new Pair(Arrays.asList(devs.get(2), devs.get(3))));
			pairs.addPair("track3", new Pair(Arrays.asList(devs.get(4), devs.get(5))));
			result.add(pairs);
		}
		return result;
	}
	
	private PairCombinations getPairsListWithNoHistoryForLastThreeDays(List<Developer> devs) {
		ArrayList<DayPairs> result = new ArrayList<DayPairs>();
		for(int i = 1; i < 4 ; i++){
			DayPairs pairs = new DayPairs();
			pairs.setDate(getPastDate(i));
			pairs.addPair("track1", new Pair(Arrays.asList(devs.get(0), devs.get(1))));
			result.add(pairs);
		}
		
		for(int i = 5; i < 6 ; i++){
			DayPairs pairs = new DayPairs();
			pairs.setDate(getPastDate(i));
			pairs.addPair("track1", new Pair(Arrays.asList(devs.get(2), devs.get(3))));
			result.add(pairs);
		}
		return new DevPairCombinations(result);
	}
	
	@Test
	public void testRotateSoloPair() {
		Pair soloPair = new Pair(Arrays.asList(new Developer("dev3")));
		List<DayPairs> pairs = getPairsListFromDevs(getStandardDevs());
		for (DayPairs dayPairs : pairs) {
			dayPairs.addPair("track2", soloPair);
		}
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(new DevPairCombinations(pairs), Arrays.asList(new Developer("dev1"), new Developer("dev2"), new Developer("dev3")));
		DayPairs todayPairs = pairs.get(0);
	
		assertThat(todayPairs.getPairByTrack("track2"), is(soloPair));
		
		DevPairCombinations newCombinations = new DevPairCombinations(pairs.subList(1, pairs.size()));
		subject.rotateSoloPairIfAny(todayPairs, newCombinations, pairsWeight);
		
		assertThat(todayPairs.getPairByTrack("track2"), is(not(soloPair)));
	}
		
	@Test
	public void testRotateSoloPairWithoutState() {
		List<DayPairs> pairs = new ArrayList<>();
		Map<Pair, Integer> pairsWeight = subject.buildPairsWeightFromPastPairing(new DevPairCombinations(pairs), Arrays.asList(new Developer("dev1"), new Developer("dev2"), new Developer("dev3")));

		subject.rotateSoloPairIfAny(new DayPairs(), new DevPairCombinations(pairs), pairsWeight);
	}
	
	@Test
	public void testBuildBuildPairsWeightFromPastPairingWhenAny() {
		PairCombinations pairCombinations = getPairsList();
		pairCombinations.getPastPairByTrack(0, "track1").setBuildPair(true);
		pairCombinations.getPastPairByTrack(1, "track2").setBuildPair(true);
		pairCombinations.getPastPairByTrack(2, "track1").setBuildPair(true);
		List<Developer> devs = getStandardDevs();
		
		Map<Pair, Integer> pairsWeight = subject.buildBuildPairsWeightFromPastPairing(pairCombinations, devs);
		
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev4")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4")))), is(1));
	}
	
	@Test
	public void testBuildBuildPairsWeightFromPastPairingWhenNoInitialWeight() {
		PairCombinations pairCombinations = getPairsList();
		pairCombinations.getPastPairByTrack(2, "track1").setBuildPair(true);
		List<Developer> devs = Arrays.asList(new Developer("dev5"), new Developer("dev6"));
		
		Map<Pair, Integer> pairsWeight = subject.buildBuildPairsWeightFromPastPairing(pairCombinations, devs);
		
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev3")))), is(nullValue()));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev5"), new Developer("dev6")))), is(0));
	}
	
	@Test
	public void testBuildBuildPairsWeightFromPastPairingWhenNon() {
		PairCombinations pairCombinations = getPairsList();
		List<Developer> devs = getStandardDevs();
		
		Map<Pair, Integer> pairsWeight = subject.buildBuildPairsWeightFromPastPairing(pairCombinations, devs);
		
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev4")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4")))), is(0));
	}
	
	@Test
	public void testBuildCommunityPairsWeightFromPastPairingWhenAny() {
		PairCombinations pairCombinations = getPairsList();
		pairCombinations.getPastPairByTrack(0, "track1").setCommunityPair(true);
		pairCombinations.getPastPairByTrack(1, "track2").setCommunityPair(true);
		pairCombinations.getPastPairByTrack(2, "track1").setCommunityPair(true);
		List<Developer> devs = getStandardDevs();
		
		Map<Pair, Integer> pairsWeight = subject.buildCommunityPairsWeightFromPastPairing(pairCombinations, devs);
		
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev4")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4")))), is(1));
	}
	
	@Test
	public void testBuildCommunityPairsWeightFromPastPairingWhenNoInitialWeight() {
		PairCombinations pairCombinations = getPairsList();
		pairCombinations.getPastPairByTrack(2, "track1").setCommunityPair(true);
		List<Developer> devs = Arrays.asList(new Developer("dev5"), new Developer("dev6"));
		
		Map<Pair, Integer> pairsWeight = subject.buildCommunityPairsWeightFromPastPairing(pairCombinations, devs);
		
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(1));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev3")))), is(nullValue()));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev5"), new Developer("dev6")))), is(0));
	}
	
	@Test
	public void testBuildCommunityPairsWeightFromPastPairingWhenNon() {
		PairCombinations pairCombinations = getPairsList();
		List<Developer> devs = getStandardDevs();
		
		Map<Pair, Integer> pairsWeight = subject.buildCommunityPairsWeightFromPastPairing(pairCombinations, devs);
		
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev4")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev3")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev2"), new Developer("dev4")))), is(0));
		assertThat(pairsWeight.get(new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4")))), is(0));
	}
	
	@Test
	public void testBuildDevTracksWeightFromPastPairingWhenAny() {
		PairCombinations pairCombinations = getPairsList();
		List<Developer> devs = getStandardDevs();
		
		subject.buildDevTracksWeightFromPastPairing(pairCombinations, devs);
		
		assertThat(devs.get(devs.indexOf(new Developer("dev1"))).getTrackWeight("track1"), is(3));
		assertThat(devs.get(devs.indexOf(new Developer("dev1"))).getTrackWeight("track2"), is(0));
		assertThat(devs.get(devs.indexOf(new Developer("dev2"))).getTrackWeight("track1"), is(2));
		assertThat(devs.get(devs.indexOf(new Developer("dev2"))).getTrackWeight("track2"), is(1));
		assertThat(devs.get(devs.indexOf(new Developer("dev3"))).getTrackWeight("track1"), is(0));
		assertThat(devs.get(devs.indexOf(new Developer("dev3"))).getTrackWeight("track2"), is(3));
		assertThat(devs.get(devs.indexOf(new Developer("dev4"))).getTrackWeight("track1"), is(1));
		assertThat(devs.get(devs.indexOf(new Developer("dev4"))).getTrackWeight("track2"), is(2));
	}
	
	@Test
	public void testBuildDevTracksWeightFromPastPairingWhenNoInitialWeight() {
		PairCombinations pairCombinations = getPairsList();
		List<Developer> devs = Arrays.asList(new Developer("dev5"), new Developer("dev6"));
		
		subject.buildDevTracksWeightFromPastPairing(pairCombinations, devs);
		
		
		assertThat(devs.get(devs.indexOf(new Developer("dev5"))).getTrackWeight("track1"), is(0));
		assertThat(devs.get(devs.indexOf(new Developer("dev5"))).getTrackWeight("track2"), is(0));
		assertThat(devs.get(devs.indexOf(new Developer("dev6"))).getTrackWeight("track1"), is(0));
		assertThat(devs.get(devs.indexOf(new Developer("dev6"))).getTrackWeight("track2"), is(0));
	}
	
	@Test
	public void testSetBuildPairWithoutWeights() {		
		List<Developer> devs = getStandardDevs();
		Map<Pair, Integer> pairsWeight = subject.buildBuildPairsWeightFromPastPairing(getPairsList(), devs);
		List<Pair> todayPairs = Arrays.asList(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2"))), new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4"))));
		
		subject.setBuildPair(todayPairs, pairsWeight);
		
		if(todayPairs.get(0).isBuildPair()){
			assertThat(todayPairs.get(1).isBuildPair(), is(false));
		}else {
			assertThat(todayPairs.get(1).isBuildPair(), is(true));
		}
	}
	
	@Test
	public void testSetBuildPairWithDifferentWeights() {		
		List<Developer> devs = getStandardDevs();				
		PairCombinations pairCombinations = getPairsList();
		pairCombinations.getPastPairByTrack(1, "track2").setBuildPair(true);
		Map<Pair, Integer> pairsWeight = subject.buildBuildPairsWeightFromPastPairing(pairCombinations, devs);
		List<Pair> todayPairs = Arrays.asList(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2"))), new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4"))));
		
		subject.setBuildPair(todayPairs, pairsWeight);
		
		assertThat(todayPairs.get(0).isBuildPair(), is(true));
		assertThat(todayPairs.get(1).isBuildPair(), is(false));
	}
	
	@Test
	public void testSetBuildPairWithMissingWeights() {		
		Map<Pair, Integer> pairsWeight = new HashMap<Pair, Integer>();
		List<Pair> todayPairs = Arrays.asList(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2"))), new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4"))));
		
		subject.setBuildPair(todayPairs, pairsWeight);
		
		
		if(todayPairs.get(0).isBuildPair()){
			assertThat(todayPairs.get(1).isBuildPair(), is(false));
		}else {
			assertThat(todayPairs.get(1).isBuildPair(), is(true));
		}
	}
	
	@Test
	public void testSetCommunityPairWithoutWeightsAndBuildPairAvailable() {		
		List<Developer> devs = getStandardDevs();
		Map<Pair, Integer> pairsWeight = subject.buildCommunityPairsWeightFromPastPairing(getPairsList(), devs);
		Pair buildPair = new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")));
		buildPair.setBuildPair(true);
		List<Pair> todayPairs = Arrays.asList(buildPair, new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4"))));
		
		subject.setCommunityPair(todayPairs, pairsWeight);
		
		assertThat(todayPairs.get(0).isCommunityPair(), is(false));
		assertThat(todayPairs.get(1).isCommunityPair(), is(true));
	}
	
	@Test
	public void testSetBuildPairWithDifferentWeightsAndMinWeightIsBuildPair() {		
		List<Developer> devs = getStandardDevs();				
		PairCombinations pairCombinations = getPairsList();
		pairCombinations.getPastPairByTrack(1, "track2").setCommunityPair(true);
		Map<Pair, Integer> pairsWeight = subject.buildCommunityPairsWeightFromPastPairing(pairCombinations, devs);
		Pair buildPair = new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")));
		buildPair.setBuildPair(true);
		List<Pair> todayPairs = Arrays.asList(buildPair, new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4"))));
		
		subject.setCommunityPair(todayPairs, pairsWeight);
		
		assertThat(todayPairs.get(0).isCommunityPair(), is(false));
		assertThat(todayPairs.get(1).isCommunityPair(), is(true));
	}
	
	@Test
	public void testSetCommunityPairWithMissingWeights() {		
		Map<Pair, Integer> pairsWeight = new HashMap<Pair, Integer>();
		List<Pair> todayPairs = Arrays.asList(new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2"))), new Pair(Arrays.asList(new Developer("dev3"), new Developer("dev4"))));
		
		subject.setCommunityPair(todayPairs, pairsWeight);
		
		
		if(todayPairs.get(0).isCommunityPair()){
			assertThat(todayPairs.get(1).isCommunityPair(), is(false));
		}else {
			assertThat(todayPairs.get(1).isCommunityPair(), is(true));
		}
	}
	
	@Test
	public void testSetCommunityPairOnePairAvailable() {
		Pair pair = new Pair(Arrays.asList(new Developer("dev1"), new Developer("dev2")));
		pair.setBuildPair(true);
		List<Pair> todayPairs = Arrays.asList(pair);
		
		subject.setCommunityPair(todayPairs, new HashMap<Pair, Integer>());
		
		assertThat(todayPairs.get(0).isCommunityPair(), is(true));
	}
	
	@Test
	public void testGetPossibleTracks() {
		List<Developer> todayDevs = getStandardDevs();
		List<String> todayTracks = Arrays.asList("track1", "track2", "track3", "track4");
		
		List<String> possibleTracks = subject.getPossibleTracks(todayTracks, todayDevs, getStandardCompanies());
		
		assertThat(possibleTracks, is(Arrays.asList("track1", "track2")));
	}
	
	@Test
	public void testGetPossibleTracksWithOddNumber() {
		List<Developer> todayDevs = Arrays.asList(new Developer("dev1"));
		List<String> todayTracks = Arrays.asList("track1", "track2", "track3", "track4");
		
		List<String> possibleTracks = subject.getPossibleTracks(todayTracks, todayDevs, getStandardCompanies());
		
		assertThat(possibleTracks, is(Arrays.asList("track1")));
	}
	
	@Test
	public void testGetPossibleTracksWithCompanyTrackWithDevs() {
		List<Developer> todayDevs = getStandardDevs();
		Company companyFoo = new Company("FOO");
		Company companyBar = new Company("BAR");
		todayDevs.get(0).setCompany(companyFoo);
		todayDevs.get(1).setCompany(companyFoo);
		List<Company> companies = Arrays.asList(companyFoo, companyBar);
		List<String> todayTracks = Arrays.asList("foo-track1", "track2", "track3", "track4");
		
		List<String> possibleTracks = subject.getPossibleTracks(todayTracks, todayDevs, companies);
		
		assertThat(possibleTracks, is(Arrays.asList("foo-track1", "track2")));
	}
	
	@Test(expected =  RuntimeException.class)
	public void testGetPossibleTracksWithCompanyTrackNoDevs() {
		List<Developer> todayDevs = getStandardDevs();
		Company companyFoo = new Company("FOO");
		Company companyBar = new Company("BAR");
		todayDevs.forEach(dev -> dev.setCompany(companyFoo));
		List<Company> companies = Arrays.asList(companyFoo, companyBar);
		List<String> todayTracks = Arrays.asList("bar-track1", "track2", "track3", "track4");
		
		subject.getPossibleTracks(todayTracks, todayDevs, companies);
	}
	
	@Test(expected =  RuntimeException.class)
	public void testGetPossibleTracksWithCompanyTrackSoloDev() {
		List<Developer> todayDevs = getStandardDevs();
		Company companyFoo = new Company("FOO");
		Company companyBar = new Company("BAR");
		todayDevs.forEach(dev -> dev.setCompany(companyFoo));
		todayDevs.get(0).setCompany(companyBar);
		List<Company> companies = Arrays.asList(companyFoo, companyBar);
		List<String> todayTracks = Arrays.asList("bar-track1", "track2", "track3", "track4");
		
		subject.getPossibleTracks(todayTracks, todayDevs, companies);
	}
	
	private Date getPastDate(int daysCountToPast) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.DATE, -(daysCountToPast));
		return cal.getTime();
	}
}
