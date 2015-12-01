package com.github.davidmoten.grumpy.wms.demo;

import static com.github.davidmoten.grumpy.core.Position.position;
import static com.github.davidmoten.grumpy.wms.RendererUtil.draw;
import static com.github.davidmoten.grumpy.wms.RendererUtil.fill;
import static com.github.davidmoten.grumpy.wms.RendererUtil.toPathGreatCircle;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.davidmoten.grumpy.core.Position;
import com.github.davidmoten.grumpy.projection.Projector;
import com.github.davidmoten.grumpy.wms.Layer;
import com.github.davidmoten.grumpy.wms.LayerFeatures;
import com.github.davidmoten.grumpy.wms.RendererUtil;
import com.github.davidmoten.grumpy.wms.WmsRequest;
import com.github.davidmoten.grumpy.wms.WmsUtil;

public class CustomLayer implements Layer {

	private static final Logger log = LoggerFactory
			.getLogger(CustomLayer.class);

	private static final String PLACE = "Canberra";
	private static final double PLACE_LAT = -35.3075;
	private static final double PLACE_LON = 149.1244;
	private final List<Position> box;

	private final LayerFeatures features;

	public CustomLayer() {
		// prepare a box around place
		box = new ArrayList<Position>();
		box.add(position(PLACE_LAT - 2, PLACE_LON - 4));
		box.add(position(PLACE_LAT + 2, PLACE_LON - 4));
		box.add(position(PLACE_LAT + 2, PLACE_LON + 4));
		box.add(position(PLACE_LAT - 2, PLACE_LON + 4));
		box.add(position(PLACE_LAT - 2, PLACE_LON - 4));

		features = LayerFeatures.builder().name("Custom").crs("EPSG:4326")
				.crs("EPSG:3857").queryable().build();
	}

	@Override
	public void render(Graphics2D g, WmsRequest request) {

		log.info("scale=" + WmsUtil.getScale(request));

		Projector projector = WmsUtil.getProjector(request);
		// only start the logic to load the data and schedule refreshes once
		// get the limits of the request box in lats and longs so we can use an
		// rtree
		Position min = projector.toPositionFromSrs(request.getBounds()
				.getMinX(), request.getBounds().getMinY());
		Position max = projector.toPositionFromSrs(request.getBounds()
				.getMaxX(), request.getBounds().getMaxY());
		log.info("min=" + min + ", max=" + max);

		RendererUtil.useAntialiasing(g);

		// get the box around place as a shape
		List<GeneralPath> shapes = toPathGreatCircle(projector, box);

		// fill the box with white
		// transparency is deferred to the wms client framework
		g.setColor(Color.white);
		fill(g, shapes);

		// draw border in blue
		g.setColor(Color.blue);
		draw(g, shapes);

		// label place
		Point p = projector.toPoint(PLACE_LAT, PLACE_LON);
		g.setColor(Color.RED);
		g.setFont(g.getFont().deriveFont(24.0f).deriveFont(Font.BOLD));
		g.drawString(PLACE, p.x + 5, p.y);

	}

	@Override
	public String getInfo(Date time, WmsRequest request, Point point,
			String mimeType) {

		// if user clicks within Canberra box then return some info, otherwise
		// return blank string

		Projector projector = WmsUtil.getProjector(request);
		Position position = projector.toPosition(point.x, point.y);

		if (position.isWithin(box))
			return "<div style=\"width:200px\">"
					+ "<p>Canberra is the capital city of Australia. With a population of 381,488, it is Australia's largest inland city and the eighth-largest city overall.</p>"
					+ "<img src=\"http://international.cit.edu.au/__data/assets/image/0006/27636/Canberra-Aerial-view-of-lake.jpg\" width=\"200\"/>"
					+ "</div>";
		else
			return "";
	}

	@Override
	public LayerFeatures getFeatures() {
		return features;
	}

}
