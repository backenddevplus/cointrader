package org.cryptocoinpartners.module;

import java.util.Random;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.util.MathUtil;
import org.joda.time.Instant;
import org.slf4j.Logger;

/**
 * @author Tim Olson
 */
@SuppressWarnings("FieldCanBeLocal")
public class MockTicker {

	@Inject
	public MockTicker(Context context, Configuration config) {
		this.context = context;
		String marketStr = config.getString("faketicker.exchange");
		if (marketStr == null)
			throw new ConfigurationError("MockTicker must be configured with the \"mockticker.exchange\" property");
		for (String marketName : marketStr.toUpperCase().split(",")) {
			String upperMarket = marketName.toUpperCase();
			Exchange exchange = Exchange.forSymbolOrCreate(upperMarket);
			if (exchange == null)
				throw new ConfigurationError("Could not find Exchange with symbol \"" + upperMarket + "\"");
			for (Market market : Market.find(exchange)) {

				new Thread(new PoissonTickerThread(market)).start();

			}
		}
	}

	public void stop() {
		running = false;
	}

	private double nextVolume() {
		return volumeBasis * MathUtil.getPoissonRandom(averageVolume);
	}

	private double nextPrice() {
		double delta = random.nextGaussian() * priceMovementStdDev;
		double multiple;
		if (delta < 0)
			multiple = 1 / (1 - delta);
		else
			multiple = 1 + delta;
		currentPrice *= multiple;
		return currentPrice;
	}

	private class PoissonTickerThread extends Thread {
		@Override
		public void run() {
			log.debug("running mock ticker");
			running = true;
			while (running) {
				try {
					double lambda = 1 / averageTimeBetweenTrades;
					double poissonSleep = -Math.log(1d - random.nextDouble()) / lambda;
					sleep((long) (1000 * poissonSleep));
				} catch (InterruptedException e) {
					break;
				}
				if (!running)
					break;
				Trade trade = Trade.fromDoubles(market, Instant.now(), null, nextPrice(), nextVolume());
				context.publish(trade);
			}
		}

		private PoissonTickerThread(Market market) {
			setDaemon(true);
			this.market = market;
		}

		private final Market market;
	}

	private final double averageTimeBetweenTrades = 2;
	private final double priceMovementStdDev = 0.0001;
	private final double averageVolume = 100.0;
	private final double volumeBasis = 1 / 1000.0;

	private final Random random = new Random();
	private double currentPrice = 100;
	private volatile boolean running;

	private final Context context;
	@Inject
	private Logger log;
}
