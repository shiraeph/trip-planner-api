// package com.travel.travelplanner.trip.service.generator;

// import com.travel.travelplanner.trip.domain.TripPlan;
// import com.travel.travelplanner.trip.domain.enums.ItineraryItemType;
// import com.travel.travelplanner.trip.domain.enums.TimeBlock;
// import com.travel.travelplanner.trip.domain.itinerary.*;

// import java.util.List;

// import org.springframework.stereotype.Component;

// @Component
// public class MockTripItineraryGenerator implements TripItineraryGenerator {

//     @Override
//     public Itinerary generate(TripPlan tripPlan) {
//         DayPlan day1 = new DayPlan(
//                 tripPlan.getStartDate(),
//                 "Arrival + City Highlights",
//                 List.of(
//                         new BlockPlan(TimeBlock.MORNING, List.of(
//                                 new ItineraryItem(ItineraryItemType.FOOD, "Coffee + local breakfast",
//                                         new Location("City Center", null, null),
//                                         "Start easy and get oriented", null, 60)
//                         )),
//                         new BlockPlan(TimeBlock.AFTERNOON, List.of(
//                                 new ItineraryItem(ItineraryItemType.ATTRACTION, "Main landmark walk",
//                                         new Location("Historic District", null, null),
//                                         "Take photos and enjoy the vibe", null, 120)
//                         )),
//                         new BlockPlan(TimeBlock.EVENING, List.of(
//                                 new ItineraryItem(ItineraryItemType.FOOD, "Dinner in a local neighborhood",
//                                         null,
//                                         "Pick a well-rated spot nearby", null, 90)
//                         ))
//                 )
//         );

//         return new Itinerary(
//                 "A balanced 1-day starter plan tailored to your preferences.",
//                 List.of("Wear comfortable shoes", "Book popular attractions in advance"),
//                 List.of(day1)
//         );
//     }

    
// }
