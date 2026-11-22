package com.fixmate.app.data;

import com.fixmate.app.model.Service;
import com.fixmate.app.model.CleaningService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ServiceCatalog {
    private ServiceCatalog() {
    }

    public static List<CleaningService> cleaningServices() {
        List<CleaningService> list = new ArrayList<>();
        list.add(new CleaningService("festive_home_help", "Festive Home Help", "1.5 hrs", 199, 0,
                new String[]{"General tidying & dusting", "Surface wipe-down", "Waste collection", "Floor sweeping & mopping"},
                new String[]{"Deep scrubbing", "Appliance cleaning", "Laundry", "Outside-premises work"}));
        list.add(new CleaningService("hourly_cleaning", "Hourly Cleaning Service", "", 79, 99,
                new String[]{"General room cleaning", "Dusting surfaces", "Floor sweeping & mopping", "Waste disposal"},
                new String[]{"Deep scrubbing", "Bathroom cleaning", "Kitchen appliances", "Laundry"}));
        list.add(new CleaningService("packing_unpacking", "Packing & Unpacking", "", 59, 79,
                new String[]{"Box packing with care", "Labelling boxes", "Unpacking & arranging", "Waste disposal after unpacking"},
                new String[]{"Furniture assembly", "Heavy lifting beyond standard items", "Electrical connections", "Painting or repairs"}));
        list.add(new CleaningService("kitchen_cleaning", "Kitchen Cleaning", "", 79, 99,
                new String[]{"Counter & slab wipe-down", "Sink scrubbing", "Stovetop cleaning", "Cabinet exterior wipe", "Floor mopping"},
                new String[]{"Inside cabinet cleaning", "Chimney or exhaust deep clean", "Appliance interior cleaning", "Pest treatment"}));
        list.add(new CleaningService("fridge_cleaning", "Fridge Cleaning", "", 59, 0,
                new String[]{"Interior shelf & drawer wipe", "Door seal cleaning", "Exterior surface wipe", "Odour removal"},
                new String[]{"Repair or servicing", "Gas refilling", "Electrical work", "Freezer deep defrost"}));
        list.add(new CleaningService("kitchen_prep", "Kitchen Prep", "", 119, 159,
                new String[]{"Vegetable & ingredient prep", "Basic chopping & sorting", "Counter setup", "Utensil arrangement"},
                new String[]{"Cooking", "Grocery shopping", "Appliance operation", "Serving or plating"}));
        list.add(new CleaningService("bathroom_cleaning", "Bathroom Cleaning", "", 79, 99,
                new String[]{"Toilet scrubbing & disinfection", "Sink & tap cleaning", "Floor scrubbing", "Mirror & surface wipe", "Waste bin emptying"},
                new String[]{"Plumbing repairs", "Tile re-grouting", "Pest treatment", "Exhaust fan servicing"}));
        return list;
    }

    public static List<String> categories() {
        return Arrays.asList("Home Repair", "Roadside Help", "Cleaning & Care", "Appliances", "Others");
    }

    public static List<Service> services() {
        List<Service> services = new ArrayList<>();
        services.add(new Service("ac", "AC", "AC Repair & Service", "Home Repair", "AC inspection, cleaning, gas refilling, performance check, and repair replacement.", 499, "Same day", "4.7"));
        services.add(new Service("electrician", "EL", "Electrician", "Home Repair", "Switchboard, wiring, fan, MCB, socket, inverter, and urgent electrical repair.", 299, "45 min", "4.8"));
        services.add(new Service("plumbing", "PL", "Plumber", "Home Repair", "Tap, sink, flush tank, blocked drain, water motor, leakage, and pipe repair.", 299, "45 min", "4.7"));
        services.add(new Service("carpenter", "CR", "Carpenter", "Home Repair", "Door, lock, curtain rod, furniture assembly, wall shelf, and cabinet fixes.", 299, "Same day", "4.8"));
        services.add(new Service("painting", "PT", "Painting", "Home Repair", "Wall painting, touch-ups, damp patch repair, rental move-out paint, and finish work.", 399, "Scheduled", "4.6"));
        services.add(new Service("interior", "IN", "Interior Design", "Others", "Consultation for room planning, furniture placement, lighting, and home styling.", 0, "Consultation", "4.9"));
        services.add(new Service("roadside", "RS", "Roadside Assistance", "Roadside Help", "Emergency roadside help, jump-start support, towing coordination, and tyre help.", 0, "24/7", "4.6"));
        services.add(new Service("cleaning", "CL", "Cleaning Services", "Cleaning & Care", "Home and office cleaning, bathroom cleaning, sofa cleaning, and deep cleaning.", 599, "Scheduled", "4.7"));
        services.add(new Service("laundry", "LD", "Laundry", "Cleaning & Care", "Clothes washing, ironing, dry cleaning pickup, stain care, and doorstep laundry service.", 199, "Scheduled", "4.6"));
        services.add(new Service("pest", "PC", "Pest Control", "Cleaning & Care", "Safe pest treatment for home, kitchen, bathroom, and office spaces.", 499, "Safe", "4.6"));
        services.add(new Service("appliances", "AP", "Appliances", "Appliances", "Washing machine, refrigerator, microwave, RO, chimney, and geyser inspection.", 399, "Same day", "4.7"));
        services.add(new Service("ro", "RO", "RO Service", "Appliances", "RO water purifier inspection, filter change, leakage repair, servicing, and installation.", 299, "Same day", "4.7"));
        return services;
    }
}
