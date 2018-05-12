package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.PostPersist;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.FillDao;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.FeesUtil;
import org.hibernate.Hibernate;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * A Fill represents some completion of an Order. The volume of the Fill might be less than the requested volume of the Order
 * 
 * @author Tim Olson
 */
@Entity
@Cacheable
//@Table(name = "\"Order\"",
@Table(name = "Fill", indexes = { @Index(columnList = "`order`"), @Index(columnList = "market"), @Index(columnList = "portfolio"),
		@Index(columnList = "position") })
@NamedQueries({ @NamedQuery(name = "Fill.findFill", query = "select f from Fill f where id=?1") })
//
@NamedEntityGraphs({

		// @NamedEntityGraph(name = "orderWithParentFill", attributeNodes = { @NamedAttributeNode(value = "parentFill", subgraph = "orderWithParentFillDetails") }, subgraphs = { @NamedSubgraph(name = "orderWithParentFillDetails", attributeNodes = { @NamedAttributeNode("children") }) }),
		@NamedEntityGraph(name = "fillWithChildOrders", attributeNodes = { @NamedAttributeNode("fillChildOrders") }),
		@NamedEntityGraph(name = "fillWithTransactions", attributeNodes = { @NamedAttributeNode("transactions") })

		//@NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
		//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
})
//})
//, subgraph = "childrenWithFills") }, subgraphs = { @NamedSubgraph(name = "childrenWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })
// @NamedEntityGraph(name = "fillWithChildOrders", attributeNodes = { @NamedAttributeNode(value = "fillChildOrders", subgraph = "childrenWithFills") }, subgraphs = { @NamedSubgraph(name = "childrenWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })
// @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
public class Fill extends RemoteEvent {
	private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
	private static Object lock = new Object();
	// private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	private static final String SEPARATOR = ",";
	private PositionEffect positionEffect;
	@Inject
	protected transient FillDao fillDao;
	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.fill");

	@Inject
	public Fill(@Assisted SpecificOrder order, @Assisted("fillTime") Instant time, @Assisted("fillTimeReceived") Instant timeReceived, @Assisted Market market,
			@Assisted("fillPriceCount") long priceCount, @Assisted("fillVolumeCount") long volumeCount, @Assisted String remoteKey) {
		super(time, timeReceived, remoteKey);
		this.getId();
		this.fillChildOrders = new CopyOnWriteArrayList<Order>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.priceCount = priceCount;
		this.lastBestPriceCount = priceCount;
		this.volumeCount = volumeCount;
		this.openVolumeCount = new AtomicLong(volumeCount);
		this.positionType = (openVolumeCount.get() > 0) ? PositionType.LONG : PositionType.SHORT;
		this.order = order;
		synchronized (order) {
			this.order.addFill(this);
		}
		this.remoteKey = remoteKey;
		this.market = market;
		this.portfolio = order.getPortfolio();
		this.stopAmountCount = (order.getStopAmount() != null) ? order.getStopAmount().getCount() : 0;
		this.positionEffect = order.getPositionEffect();
		//  this.id = getId();
		this.version = getVersion();

	}

	//    @Override
	//    @Basic(optional = true)
	//    public String getRemoteKey() {
	//        return remoteKey;
	//    }
	//
	//    @Override
	//    public void setRemoteKey(@Nullable String remoteKey) {
	//        this.remoteKey = remoteKey;
	//    }

	public Fill(@Assisted SpecificOrder order, @Assisted Instant time, @Assisted Instant timeReceived, @Assisted Market market, @Assisted long priceCount,
			@Assisted long volumeCount, @Assisted Amount commission, @Assisted String remoteKey) {
		super(time, timeReceived, remoteKey);
		this.getId();
		this.fillChildOrders = new CopyOnWriteArrayList<Order>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.remoteKey = remoteKey;
		this.order = order;
		synchronized (order) {
			this.order.addFill(this);
		}
		this.market = market;

		this.priceCount = priceCount;
		this.lastBestPriceCount = priceCount;
		this.volumeCount = volumeCount;
		this.openVolumeCount = new AtomicLong(volumeCount);
		this.positionType = (openVolumeCount.get() > 0) ? PositionType.LONG : PositionType.SHORT;
		this.commission = commission;
		this.portfolio = order.getPortfolio();
		this.stopAmountCount = (order.getStopAmount() != null) ? order.getStopAmount().getCount() : 0;
		this.positionEffect = order.getPositionEffect();

	}

	public <T> T find() {
		//   synchronized (persistanceLock) {
		try {
			return (T) fillDao.find(Order.class, this.getId());
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {
			return null;
			// System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}

	}

	// public @ManyToOne(cascade = { CascadeType.MERGE, CascadeType.REMOVE, CascadeType.REFRESH })
	public @ManyToOne @JoinColumn(name = "`order`") SpecificOrder getOrder() {
		return order;
	}

	@Override
	public synchronized void merge() {
		// try {
		// if (getOrder() != null)
		//   getOrder().persit();
		//  i//f (getPosition() != null)
		// getPosition().persit();
		//   if (order != null)
		//     order.find();
		try {
			//   find();
			this.setPeristanceAction(PersistanceAction.MERGE);

			this.setRevision(this.getRevision() + 1);
			log.debug("Fill - Merge : Merge of Fill " + this.getId() + " OpenVolumeCount:" + this.getOpenVolumeCount() + " called from class "
					+ Thread.currentThread().getStackTrace()[2]);

			fillDao.merge(this);
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:" + ex);

			// ex.printStackTrace();

		}

		//fillDao.merge(this);
	}

	@Override
	public synchronized EntityBase refresh() {
		log.debug("Fill - Refresh : Refresh of Fill " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);

		return fillDao.refresh(this);
	}

	@Override
	public synchronized void persit() {
		this.setPeristanceAction(PersistanceAction.NEW);
		this.setRevision(this.getRevision() + 1);

		// Any @ManyToOne I need to persist first to get them in the EM context.
		//   synchronized (persistanceLock) {
		//  if (this.hasFills()) {
		//    for (Fill fill : this.getFills())

		//PersistUtil.merge(fill);
		//}
		// if (getOrder() != null)
		//   getOrder().persit();
		// if (getPosition() != null)
		//   getPosition().persit();

		//List<Fill> duplicate = fillDao.queryList(Fill.class, "select f from Fill f where f=?1", this);

		//if (duplicate == null || duplicate.isEmpty())
		// if (fillDao != null)
		// avoid session conflicts due to lazy loading.
		//for (Order childOrder : this.getFillChildOrders())
		//    childOrder.p
		//  childOrder.persit();
		// try {
		log.debug("Fill - Persist : Persit of Fill " + this.getId() + " OpenVolumeCount:" + this.getOpenVolumeCount() + " called from class "
				+ Thread.currentThread().getStackTrace()[2]);
		fillDao.persist(this);
		//  } catch (Exception | Error ex) {
		//   fillDao.merge(this);

		//    System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
		//ex.printStackTrace();

		//  }

		//  PersistUtil.insert(this);
		//else
		//  fillDao.merge(this);
		//  PersistUtil.merge(this);
		//  }
		//Iterator<Order> itc = getChildren().iterator();
		//while (itc.hasNext()) {
		//                //  for (Fill pos : getFills()) {
		//  Order order = itc.next();

		// order.persit();
		// }

		//   synchronized (persistanceLock) {
		//  if (this.hasFills()) {
		//    for (Fill fill : this.getFills())

		//PersistUtil.merge(fill);
		//}
		//if (this.hasFills()) {
		// for (Fill fill : getFills()) {
		// if (this.hasChildren()) {
		//   for (Order order : this.getChildren())
		//     if (order.getParentFill() == this)
		//       order.persit();
		//PersistUtil.merge(order);
		// }

	}

	@Nullable
	@ManyToOne(optional = true)
	//, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	//cascade = { CascadeType.ALL })
	@JoinColumn(name = "position")
	public Position getPosition() {

		return position;
	}

	@Transient
	@Nullable
	public Double getPriceAsDouble() {
		Amount price = getPrice();
		return price == null ? null : price.asDouble();
	}

	@Transient
	@Nullable
	public Double getPriceCountAsDouble() {
		Long price = getPriceCount();
		return price == null ? null : price.doubleValue();
	}

	@Transient
	@Nullable
	public Double getLastBestPriceCountAsDouble() {
		Long price = getLastBestPriceCount();
		return price == null ? null : price.doubleValue();
	}

	@Transient
	public boolean isLong() {
		if (getOpenVolume() == null)
			return getOpenVolume().isZero();
		return getOpenVolume().isPositive();
	}

	public synchronized void addChildOrder(Order order) {
		if (!getFillChildOrders().contains(order))

			getFillChildOrders().add(order);

	}

	public synchronized void removeChildOrder(Order order) {
		order.setParentOrder(null);
		getFillChildOrders().remove(order);
	}

	@Override
	@PostPersist
	public synchronized void postPersist() {
		//detach();
	}

	// @PrePersist
	//  @Override
	//  public void prePersist() {
	//
	//  }
	@PrePersist
	@Override
	public synchronized void prePersist() {
		//this.persitParents();

		//for (Order childorder : getFillChildOrders())
		//	getDao().merge(childorder);

	}

	@Override
	public Fill clone() {
		Fill clone = null;
		try {
			clone = (Fill) super.clone();
			clone.transactions = new ArrayList(this.transactions);
			clone.fillChildOrders = new ArrayList(this.fillChildOrders);

			//deep copying 
		} catch (CloneNotSupportedException cns) {
			log.error("Error while cloning fill", cns);
		}
		return clone;
	}

	@Override
	public synchronized void detach() {

		fillDao.detach(this);
	}

	//@Nullable
	//@OneToMany(cascade = CascadeType.MERGE)
	//(mappedBy = "parentFill")
	//@OrderBy
	//@Transient

	//@OneToMany(mappedBy = "parentFill")
	// @OneToMany(orphanRemoval = true)
	// @JoinColumn(name = "parentFill")
	//@Column
	//@ElementCollection(targetClass = Order.class)
	@Nullable
	@OneToMany
	//(cascade = CascadeType.PERSIST)
	//, mappedBy = "order")
	(mappedBy = "parentFill")
	//, cascade = CascadeType.MERGE)
	// @OrderColumn(name = "version")
	//  @OrderColumn(name = "time")
	//, fetch = FetchType.EAGER)
	//, cascade = CascadeType.MERGE)
	//, fetch = FetchType.LAZY)
	@OrderBy
	public List<Order> getFillChildOrders() {
		if (fillChildOrders == null)
			fillChildOrders = new CopyOnWriteArrayList<Order>();
		//  synchronized (//) {
		return fillChildOrders;
		// }
	}

	protected synchronized void setFillChildOrders(List<Order> children) {
		// log.trace("Fill:setFillChildOrders setting child orders [ ] to " + System.identityHashCode(children) + " for fill " + getId() + " / "
		//   + System.identityHashCode(this) + "Calling class " + Thread.currentThread().getStackTrace()[2]);
		////     for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
		//     log.error(ste.toString());
		// }
		this.fillChildOrders = children;
	}

	@Transient
	public boolean hasChildren() {
		return !getFillChildOrders().isEmpty();
	}

	@Transient
	public boolean hasTransaction() {
		return !getTransactions().isEmpty();
	}

	@Transient
	public boolean isShort() {

		return getOpenVolume().isNegative();
	}

	@Nullable
	// @OneToMany(mappedBy = "fill")
	//, orphanRemoval = true, cascade = CascadeType.REMOVE)
	// , fetch = FetchType.EAGER)
	// , cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	//, mappedBy = "fill")
	//(fetch = FetchType.EAGER)
	// @Transient
	@OneToMany
	//(cascade = CascadeType.PERSIST)
	//, mappedBy = "order")
	(mappedBy = "fill")
	//, cascade = CascadeType.MERGE)
	//, fetch = FetchType.EAGER)
	//, cascade = CascadeType.MERGE)
	//, fetch = FetchType.LAZY)
	@OrderBy
	public List<Transaction> getTransactions() {
		if (transactions == null)
			transactions = new CopyOnWriteArrayList<Transaction>();

		//synchronized (lock) {
		return transactions;
		// }
	}

	public synchronized void addTransaction(Transaction transaction) {

		// synchronized (lock) {
		if (transactions == null)
			log.debug("test");
		synchronized (getTransactions()) {
			if (!getTransactions().contains(transaction))

				getTransactions().add(transaction);
		}
		// this.transactions.add(transaction);
		//  }
	}

	public synchronized void removeTransaction(Transaction transaction) {

		transaction.setFill(null);
		synchronized (getTransactions()) {
			getTransactions().remove(transaction);
		}
	}

	protected synchronized void setTransactions(List<Transaction> transactions) {

		this.transactions = transactions;
	}

	@ManyToOne
	@JoinColumn(name = "market")
	public Market getMarket() {
		return market;
	}

	@Transient
	public DiscreteAmount getPrice() {
		//  if (getPriceCount() == 0)
		//    return null;
		if (market.getPriceBasis() == 0)
			return null;
		return new DiscreteAmount(getPriceCount(), getMarket().getPriceBasis());
	}

	@Transient
	public DiscreteAmount getLastBestPrice() {
		//  if (getPriceCount() == 0)
		//    return null;
		if (market.getPriceBasis() == 0)
			return null;
		return new DiscreteAmount(getLastBestPriceCount(), getMarket().getPriceBasis());
	}

	@Transient
	public Amount getStopPrice() {
		//     if (getStopPriceCount() == 0)
		//       return null;
		return new DiscreteAmount(getStopPriceCount(), getMarket().getPriceBasis());
	}

	@Transient
	public Amount getOriginalStopPrice() {
		//     if (getStopPriceCount() == 0)
		//       return null;
		return new DiscreteAmount(getOriginalStopPriceCount(), getMarket().getPriceBasis());
	}

	@Transient
	public Amount getTargetPrice() {
		//     if (getTargetPriceCount() == 0)
		//       return null;
		return new DiscreteAmount(getTargetPriceCount(), getMarket().getPriceBasis());
	}

	public long getPriceCount() {
		return priceCount;
	}

	public long getLastBestPriceCount() {
		return lastBestPriceCount;
	}

	public long getStopPriceCount() {
		if (getOpenVolumeCount() != 0)
			return stopPriceCount;
		else
			return 0;
	}

	public long getOriginalStopPriceCount() {
		if (getOpenVolumeCount() != 0)
			return originalStopPriceCount;
		else
			return 0;
	}

	public long getTargetPriceCount() {
		return targetPriceCount;
	}

	@Transient
	public Amount getVolume() {
		// if (getVolumeCount() == 0)
		//   return null;
		return new DiscreteAmount(getVolumeCount(), getMarket().getVolumeBasis());
	}

	public long getVolumeCount() {
		return volumeCount;
	}

	@Transient
	public Amount getOpenVolume() {
		//   if (openVolumeCount == 0)
		//     return null;
		if (openVolume == null)
			openVolume = new DiscreteAmount(getOpenVolumeCount(), getMarket().getVolumeBasis());
		return openVolume;

	}

	public long getOpenVolumeCount() {
		if (openVolumeCount != null)
			return openVolumeCount.get();
		return 0L;
	}

	@Transient
	public Amount getCommission() {
		if (commission == null)
			setCommission(FeesUtil.getCommission(this));
		return commission;
	}

	public long getHoldingTime() {

		return holdingTime;
	}

	@Transient
	public Amount getMargin() {
		if (margin == null)
			setMargin(FeesUtil.getMargin(this));

		return margin;
	}

	@Transient
	public PositionType getPositionType() {
		//  if (getOpenVolumeCount() != 0)
		if (positionType == null)
			if (getVolume().isPositive())
				return PositionType.LONG;
			else
				return PositionType.SHORT;
		// If i have children and all the children are fully filled, then I set to flat
		if (hasChildren() && getUnfilledVolume().isZero())
			return PositionType.FLAT;
		else
			return positionType;
		// return PositionType.FLAT;
	}

	@ManyToOne
	@JoinColumn(name = "portfolio")
	public Portfolio getPortfolio() {
		return portfolio;
	}

	@Transient
	public FillType getFillType() {
		if (getOrder() != null)
			return getOrder().getFillType();
		return null;
	}

	public PositionEffect getPositionEffect() {

		return positionEffect;

	}

	protected synchronized void setPositionEffect(PositionEffect positionEffect) {
		this.positionEffect = positionEffect;
	}

	@Override
	public String toString() {
		// + (order.getId() != null ? order.getId() : "")
		//   + (getFillType() != null ? getFillType() : "")
		// getVolume()
		return "Id=" + (getId() != null ? getId() : "") + SEPARATOR + "version=" + getVersion() + SEPARATOR + "revision=" + getRevision() + SEPARATOR + "time="
				+ (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "PositionType=" + (getPositionType() != null ? getPositionType() : "")
				+ SEPARATOR + "Market=" + (market != null ? market : "") + SEPARATOR + "Price=" + (getPrice() != null ? getPrice() : "") + SEPARATOR + "Volume="
				+ (getVolume() != null ? getVolume() : "") + SEPARATOR + "Unfilled Volume=" + (hasChildren() ? getUnfilledVolume() : "") + SEPARATOR
				+ "Open Volume=" + (getOpenVolume() != null ? getOpenVolume() : "") + SEPARATOR + "Open Volume Count=" + getOpenVolumeCount() + SEPARATOR
				+ "Position Effect=" + (getPositionEffect() != null ? getPositionEffect() : "") + SEPARATOR + "Position="
				+ (getPosition() != null ? getPosition().getId() : "") + SEPARATOR + "Comment="
				+ (getOrder() != null && getOrder().getComment() != null ? getOrder().getComment() : "") + SEPARATOR + "Order="
				+ (getOrder() != null ? getOrder().getId() : "") + SEPARATOR + "Order group=" + (getOrder() != null ? getOrder().getOrderGroup() : "")
				+ SEPARATOR + "Parent Fill=" + ((getOrder() != null && getOrder().getParentFill() != null) ? getOrder().getParentFill().getId() : "");
	}

	// JPA
	protected Fill() {
	}

	public synchronized void setOrder(SpecificOrder order) {
		this.order = order;
	}

	public synchronized void setMarket(Market market) {
		this.market = market;
	}

	public synchronized void setPositionType(PositionType positionType) {
		if (this.positionType == (PositionType.FLAT))
			log.debug(this.getClass().getSimpleName() + ":setPositionType - Setting postion type from FLAT to " + positionType);
		this.positionType = positionType;
	}

	@Transient
	public Amount getUnfilledVolume() {
		Amount filled = DecimalAmount.ZERO;
		Amount unfilled = getVolume();
		if (getVolume().isZero())
			return DecimalAmount.ZERO;
		for (Order childOrder : getFillChildOrders()) {

			ArrayList<Fill> allChildFills = new ArrayList<Fill>();
			childOrder.getAllFillsByParentOrder(childOrder, allChildFills);

			for (Fill childFill : allChildFills) {
				if (getVolume() == null || childFill.getVolume() == null)
					System.out.println("null fill volume");
				if (getVolume() != null && !getVolume().isZero() && (childFill.getVolume().isPositive() && getVolume().isNegative())
						|| (childFill.getVolume().isNegative() && getVolume().isPositive()))
					filled = filled.plus(childFill.getVolume());
			}
			unfilled = (getVolume().isNegative()) ? (getVolume().abs().minus(filled.abs())).negate() : getVolume().abs().minus(filled.abs());

		}
		return unfilled;

	}

	@Transient
	public Amount getWorkingVolume(OrderService orderService) {
		Amount filled = DecimalAmount.ZERO;
		Amount unfilled = getVolume();
		if (getVolume().isZero())
			return DecimalAmount.ZERO;
		for (Order childOrder : getFillChildOrders()) {
			if (!orderService.getOrderState(childOrder).isOpen())
				continue;
			ArrayList<Fill> allChildFills = new ArrayList<Fill>();
			childOrder.getAllFillsByParentOrder(childOrder, allChildFills);

			for (Fill childFill : allChildFills) {
				if (!orderService.getOrderState(childFill.getOrder()).isOpen())
					continue;
				if (getVolume() == null || childFill.getVolume() == null)
					System.out.println("null fill volume");
				if (getVolume() != null && !getVolume().isZero() && (childFill.getVolume().isPositive() && getVolume().isNegative())
						|| (childFill.getVolume().isNegative() && getVolume().isPositive()))
					filled = filled.plus(childFill.getVolume());
			}
			unfilled = (getVolume().isNegative()) ? (getVolume().abs().minus(filled.abs())).negate() : getVolume().abs().minus(filled.abs());

		}
		return unfilled;

	}

	public void loadAllChildOrdersByFill(Fill parentFill, Map<Order, Order> orders, Map<Fill, Fill> fills) {
		Map withChildOrderHints = new HashMap();
		Map withTransactionsHints = new HashMap();

		withChildOrderHints.put("javax.persistence.fetchgraph", "fillWithChildOrders");
		withTransactionsHints.put("javax.persistence.fetchgraph", "fillWithTransactions");
		Fill fillWithFills;
		Fill fillWithChildren;
		Fill fillWithTransactions;

		// Set test = new HashSet();
		log.trace("Fill:loadAllChildOrdersByFill loading child order for fill " + parentFill.getId() + ". Calling class "
				+ Thread.currentThread().getStackTrace()[2]);
		try {
			log.trace("Fill:loadAllChildOrdersByFill loading child order for parent order: " + parentFill.getOrder().getId() + "/"
					+ System.identityHashCode(parentFill.getOrder()) + " of fill " + parentFill.getId() + ". Calling class ");

			parentFill.getOrder().loadAllChildOrdersByParentOrder(parentFill.getOrder(), orders, fills);

			log.trace("Fill:loadAllChildOrdersByFill order children for parent fill: " + parentFill.getId() + ". Calling class ");

			fillWithChildren = EM.namedQueryZeroOne(Fill.class, "Fill.findFill", withChildOrderHints, parentFill.getId());
			fillWithTransactions = EM.namedQueryZeroOne(Fill.class, "Fill.findFill", withTransactionsHints, parentFill.getId());
			//   orderWithChildren = EM.namedQueryZeroOne(Order.class, "Fill.findFill", withChildOrderHints, parentFill.getOrder());

		} catch (Error | Exception ex) {
			log.error("Fill:loadAllChildOrdersByFill unable to get fill for fillID: " + parentFill.getId(), ex);
			return;
		}

		//  parentFill.getOrder().loadAllChildOrdersByParentOrder(parentFill.getOrder(), orders, fills);

		//   .loadAllChildOrdersByParentOrder()

		if (parentFill.getPosition() != null && parentFill.getPortfolio().getPositions().contains(parentFill.getPosition())) {
			synchronized (parentFill.getPortfolio()) {

				for (Position fillPosition : parentFill.getPortfolio().getPositions()) {
					if (fillPosition.equals(parentFill.getPosition())) {
						parentFill.setPosition(fillPosition);
						break;
					}
				}
			}
		}

		if (fillWithChildren != null && fillWithChildren.getFillChildOrders() != null && fillWithChildren.getId().equals(parentFill.getId())
				&& Hibernate.isInitialized(fillWithChildren.getFillChildOrders())) {
			int index = 0;
			for (Order order : fillWithChildren.getFillChildOrders()) {
				log.debug("Fill:loadAllChildOrdersByFill loading child order " + order.getId() + "/" + System.identityHashCode(order) + " for fill "
						+ parentFill.getId());

				if (order.getPortfolio().equals(parentFill.getPortfolio()))
					order.setPortfolio(parentFill.getPortfolio());

				if (order.getParentOrder() != null && order.getParentOrder().getPortfolio().equals(parentFill.getPortfolio()))
					order.getParentOrder().setPortfolio(parentFill.getPortfolio());
				if (order.getParentFill() != null && order.getParentFill().getPortfolio().equals(parentFill.getPortfolio()))
					order.getParentFill().setPortfolio(parentFill.getPortfolio());
				if (order.getPortfolio().getPositions().contains(order.getParentFill().getPosition())) {
					synchronized (order.getPortfolio()) {
						for (Position fillPosition : order.getPortfolio().getPositions()) {
							if (fillPosition.equals(order.getParentFill().getPosition())) {
								order.getParentFill().setPosition(fillPosition);

								break;
							}
						}
					}
				}
				// if (order.equals(orders.get(order)));
				//child = parentOrder;
				//     continue;
				int myHash = System.identityHashCode(orders.get(order));
				if (!orders.containsKey(order)) {
					orders.put(order, order);
					log.info("Fill:loadAllChildOrdersByFill Order not in order map, loading child order from database " + order.getId() + "/"
							+ System.identityHashCode(order) + " for fill " + parentFill.getId());
					order.loadAllChildOrdersByParentOrder(order, orders, fills);

				}
				log.debug("Fill:loadAllChildOrdersByFill setting child order index " + index + " to order  " + order.getId() + " / " +

						System.identityHashCode(order) + " for fill" + parentFill.getId() + " /" + System.identityHashCode(parentFill));
				fillWithChildren.getFillChildOrders().set(index, orders.get(order));
				log.debug("Fill:loadAllChildOrdersByFill setting parent fill to " + parentFill.getId() + " / " + System.identityHashCode(parentFill)
						+ " for order " + order.getId() + " / " + System.identityHashCode(order));
				orders.get(order).setParentFill(parentFill);

				index++;
			}
			log.debug(
					"Fill:loadAllChildOrdersByFill setting children to " + fillWithChildren.getFillChildOrders().hashCode() + " for fill" + parentFill.getId());

			parentFill.setFillChildOrders(fillWithChildren.getFillChildOrders());
		} else
			parentFill.setFillChildOrders(new CopyOnWriteArrayList<Order>());

		if (orders.containsKey(getOrder()))
			setOrder((SpecificOrder) orders.get(getOrder()));
		else if (getOrder().getPortfolio().equals(parentFill.getPortfolio())) {

			getOrder().setPortfolio(parentFill.getPortfolio());

			orders.put(order, order);
			log.debug("Fill:loadAllChildOrdersByFill order:" + getOrder().getId() + "/" + System.identityHashCode(getOrder())
					+ " not in order map, loading child orders  for fill" + parentFill.getId());

			//System.out.println(" loading child order for " + order.getId());
			getOrder().loadAllChildOrdersByParentOrder(getOrder(), orders, fills);
		}

		if (fillWithTransactions != null && fillWithTransactions.getTransactions() != null && Hibernate.isInitialized(fillWithTransactions.getTransactions())
				&& fillWithTransactions.getId().equals(parentFill.getId())) {

			for (Transaction transaction : fillWithTransactions.getTransactions()) {
				log.debug("Fill:loadAllChildOrdersByFill loading transactions " + transaction.getId() + "/" + System.identityHashCode(order) + " for fill "
						+ parentFill.getId());

				if (transaction.getPortfolio().equals(parentFill.getPortfolio()))
					transaction.setPortfolio(parentFill.getPortfolio());

				//  if (transaction.getOrder().equals(parentOrder))
				//child = parentOrder;
				//    continue;
				if (!fills.containsKey(transaction.getFill())) {
					fills.put(transaction.getFill(), transaction.getFill());

				} else
					transaction.setFill(fills.get(transaction.getFill()));

				if (transaction.getOrder() != null && !orders.containsKey(transaction.getOrder())) {
					orders.put(transaction.getOrder(), transaction.getOrder());
					if (transaction.getOrder().getPortfolio().equals(parentFill.getPortfolio()))
						transaction.getOrder().setPortfolio(parentFill.getPortfolio());
					if (transaction.getOrder().getPortfolio().equals(parentFill.getPortfolio()))
						transaction.getOrder().setPortfolio(parentFill.getPortfolio());
					if (transaction.getOrder().getParentOrder() != null
							&& transaction.getOrder().getParentOrder().getPortfolio().equals(parentFill.getPortfolio()))
						transaction.getOrder().getParentOrder().setPortfolio(parentFill.getPortfolio());

					transaction.getOrder().loadAllChildOrdersByParentOrder(transaction.getOrder(), orders, fills);
				} else if (transaction.getOrder() != null)
					transaction.setOrder(orders.get(transaction.getOrder()));

			}
			parentFill.setTransactions(fillWithTransactions.getTransactions());

		} else
			parentFill.setTransactions(new CopyOnWriteArrayList<Transaction>());

	}

	public void getAllSpecificOrdersByParentFill(Fill parentFill, Set<Order> allChildren) {
		for (Order child : parentFill.getFillChildOrders()) {
			if (child instanceof SpecificOrder) {
				if (!allChildren.contains(child)) {
					allChildren.add(child);
				}
				child.getAllSpecificOrderByParentOrder(child, allChildren);

				parentFill.getOrder().getAllSpecificOrderByParentOrder(parentFill.getOrder(), allChildren);
			}
		}
	}

	public void getAllParnetSpecificOrdersByFill(Fill parentFill, Set<Order> allChildren) {
		for (Order child : parentFill.getFillChildOrders()) {
			if (child instanceof SpecificOrder) {
				if (!allChildren.contains(child)) {
					allChildren.add(child);
				}
				child.getAllSpecificOrderByParentOrder(child, allChildren);

				parentFill.getOrder().getAllSpecificOrderByParentOrder(parentFill.getOrder(), allChildren);
			}
		}
	}

	public void getAllParentOrdersByFill(Fill fill, Collection<Order> allParents) {

		ArrayList<EntityBase> parents = new ArrayList<EntityBase>();
		getParents(fill.getParent(), parents);

		for (EntityBase parent : parents)
			if (parent instanceof Order)
				allParents.add((Order) parent);

	}

	public void getAllParentFillsByFill(Fill fill, Collection<Fill> allParents) {

		ArrayList<EntityBase> parents = new ArrayList<EntityBase>();
		getParents(fill.getParent(), parents);

		for (EntityBase parent : parents)
			if (parent instanceof Fill)
				allParents.add((Fill) parent);

	}

	public void getAllOrdersByParentFill(Collection<Order> allChildren) {
		Set<Order> allSpecificChildOrders = new HashSet<Order>();
		Set<Order> allGeneralChildOrders = new HashSet<Order>();

		getAllSpecificOrdersByParentFill(this, allSpecificChildOrders);

		getAllGeneralOrdersByParentFill(this, allGeneralChildOrders);

		allChildren.addAll(allGeneralChildOrders);
		allChildren.addAll(allSpecificChildOrders);

	}

	void getAllGeneralOrdersByParentFill(Fill parentFill, Set<Order> allChildren) {
		for (Order child : parentFill.getFillChildOrders()) {
			if (child instanceof GeneralOrder) {
				if (!allChildren.contains(child)) {
					allChildren.add(child);
				}
				child.getAllGeneralOrderByParentOrder(child, allChildren);

				parentFill.getOrder().getAllGeneralOrderByParentOrder(parentFill.getOrder(), allChildren);
			}
		}
	}

	protected synchronized void setPriceCount(long priceCount) {
		this.priceCount = priceCount;
	}

	protected synchronized void setLastBestPriceCount(long lastBestPriceCount) {
		this.lastBestPriceCount = lastBestPriceCount;
	}

	public synchronized void setStopAmountCount(long stopAmountCount) {
		this.stopAmountCount = stopAmountCount;
	}

	public synchronized void setTrailingStopAmountCount(long trailingStopAmountCount) {
		this.trailingStopAmountCount = trailingStopAmountCount;
	}

	public synchronized void setTargetAmountCount(long targetAmountCount) {
		this.targetAmountCount = targetAmountCount;
	}

	public synchronized void setStopPriceCount(long stopPriceCount) {
		if (stopPriceCount != 0) {

			this.stopPriceCount = stopPriceCount;
			setOriginalStopPriceCount(stopPriceCount);
		}
	}

	public synchronized void setOriginalStopPriceCount(long originalStopPriceCount) {
		if (this.originalStopPriceCount == 0 && originalStopPriceCount != 0)

			this.originalStopPriceCount = originalStopPriceCount;
	}

	public synchronized void setTrailingStopPriceCount(long trailingStopPriceCount) {
		if (trailingStopPriceCount != 0)

			this.trailingStopPriceCount = trailingStopPriceCount;
	}

	public synchronized void setTargetPriceCount(long targetPriceCount) {
		this.targetPriceCount = targetPriceCount;
	}

	public synchronized void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	protected synchronized void setVolumeCount(long volumeCount) {

		this.volumeCount = volumeCount;
		// this.volume = null;
	}

	public synchronized void setOpenVolumeCount(long openVolumeCount) {
		// log.trace(this.getClass().getSimpleName() + " : setOpenVolumeCount to " + openVolumeCount + "for " + this.getId() + "called from stack "
		//   + Thread.currentThread().getStackTrace()[2]);
		// for (StackTraceElement element : Thread.currentThread().getStackTrace())
		//   log.debug(element.toString());
		this.openVolume = null;
		/*		if (this.position != null)
					this.position.reset();
		*/ if (this.openVolumeCount == null)
			this.openVolumeCount = new AtomicLong(openVolumeCount);
		else
			this.openVolumeCount.set(openVolumeCount);

	}

	protected synchronized void setCommission(Amount commission) {
		this.commission = commission;
	}

	protected synchronized void setHoldingTime(long holdingTime) {
		this.holdingTime = holdingTime;
	}

	protected synchronized void setMargin(Amount margin) {
		this.margin = margin;
	}

	@Override
	@Transient
	public Dao getDao() {
		return fillDao;
	}

	@Override
	@Transient
	public synchronized void setDao(Dao dao) {
		fillDao = (FillDao) dao;
		// TODO Auto-generated method stub
		//  return null;
	}

	public synchronized void setPosition(Position position) {

		this.position = position;

	}

	private List<Order> fillChildOrders;
	private SpecificOrder order;
	private Market market;
	private long priceCount;
	private long lastBestPriceCount;

	private long stopAmountCount;
	private long trailingStopAmountCount;
	private long stopPriceCount;
	private long originalStopPriceCount;
	private long trailingStopPriceCount;
	private long targetAmountCount;
	private long targetPriceCount;
	private long volumeCount;
	private AtomicLong openVolumeCount;
	private long holdingTime;
	private DiscreteAmount openVolume;
	private Amount commission;
	private Amount margin;
	private PositionType positionType;
	private List<Transaction> transactions;
	private Portfolio portfolio;
	private Position position;

	@Override
	public synchronized void delete() {
		log.debug("Fill - Delete : Delete of Fill " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);
		// TODO Auto-generated method stub

	}

	@Override
	@Transient
	public EntityBase getParent() {

		return getOrder();
	}

	@Override
	public void persitParents() {

		if (getPortfolio() != null) {
			getDao().persist(getPortfolio());

		}

		if (getOrder() != null) {
			getDao().persist(getOrder());

		}
		if (getPosition() != null) {
			getDao().persist(getPosition());

		}

		for (Order childorder : getFillChildOrders())
			getDao().persist(childorder);
		for (Transaction transaction : getTransactions())
			getDao().persist(transaction);

	}

	// @Override
	// public void prePersist() {
	// TODO Auto-generated method stub

	// }

}
