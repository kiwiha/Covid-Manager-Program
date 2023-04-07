package view;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.temporal.ValueRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

public class Map extends BasePage {
	final int INF = Integer.MAX_VALUE;

	BufferedImage map;
	double zoom = 0.5;
	AffineTransform aff = new AffineTransform();
	Point2D sAffPoint = new Point2D.Float(), eAffPoint = new Point2D.Float();
	Point2D from, to;
	JToggleButton toggle[] = new JToggleButton[2];
	JPanel p1, p2, p2c;
	CardLayout pages;
	ArrayList<Integer> path;
	ArrayList<Object[]> objList; // type, x, y

	Object adj[][][] = new Object[346][346][2];
	{
		for (int i = 1; i < 346; i++) {
			for (int j = 1; j < 346; j++) {
				adj[i][j][0] = INF;
				if (i == j)
					adj[i][j][0] = 0;
			}
		}
	}

	ValueRange current;
	ArrayList<Object> selected;
	JTextField search, start, end;

	JPopupMenu menu = new JPopupMenu();

	public Map() {
		setLayout(new BorderLayout());
		dataInit();
		mapInit();

		for (var i : "출발지,도착지".split(",")) {
			var it = new JMenuItem(i);
			menu.add(it);
			it.addActionListener(a -> {
				if (a.getActionCommand().equals("출발지"))
					setPath(selected, 0);
				else
					setPath(selected, 1);
				dijkstra();
			});
		}
		add(c = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				var g2 = (Graphics2D) g;
				g2.drawImage(map, aff, null);
			}
		});

		var ma = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				super.mousePressed(e);
				from = e.getPoint();
				to = null;

				try {
					eAffPoint = aff.inverseTransform(e.getPoint(), new Point2D.Float());
				} catch (NoninvertibleTransformException e1) {
					e1.printStackTrace();
				}

				int clickedX = (int) eAffPoint.getX();
				int clickedY = (int) eAffPoint.getY();

				var item = objList.stream().filter(obj -> clickedX >= cint(obj[1]) && clickedX <= cint(obj[1]) + 40
						&& clickedY >= cint(obj[2]) && clickedY <= cint(obj[2]) + 40).findFirst();

				if (item.isPresent()) {
					var building = (ArrayList<Object>) item.get()[0];
					if (e.getButton() == 1) {
						if (cint(building.get(5)) == 2)
							return;
						new InfoDialog(building).setVisible(true);
					} else if (e.getButton() == 3) {
						selected = building;
						menu.show(c, e.getX(), e.getY());
					}
				}

				repaint();
			}

			public void mouseDragged(MouseEvent e) {
				try {
					to = e.getPoint();
					sAffPoint = aff.inverseTransform(from, null);
					eAffPoint = aff.inverseTransform(to, null);
					var difx = sAffPoint.getX() - eAffPoint.getX();
					var dify = sAffPoint.getY() - eAffPoint.getY();
					aff.translate(-difx, -dify);
					from = to;
					to = null;
					c.repaint();
				} catch (NoninvertibleTransformException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			};

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				try {
					eAffPoint = aff.inverseTransform(e.getPoint(), null);
					var flag = false;

					if (e.getPreciseWheelRotation() < 0) {
						if (zoom == 2)
							flag = false;
						else
							zoom = Math.min(2, zoom + 0.1);
					} else {
						if (zoom == 0.1)
							flag = true;
						else
							zoom = Math.max(0.1, zoom - 0.1);
					}

					if (!flag) {
						aff.setToIdentity();
						aff.translate(e.getX(), e.getY());
						aff.scale(zoom, zoom);
						aff.translate(-eAffPoint.getX(), -eAffPoint.getY());
						c.repaint();
					}

				} catch (NoninvertibleTransformException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		};

		c.addMouseListener(ma);
		c.addMouseMotionListener(ma);
		c.addMouseWheelListener(ma);

		c.setBackground(new Color(153, 217, 234));

		add(sz(w = new JPanel(new BorderLayout()), 280, 0), "West");

		c.add(cw = new JPanel(new GridBagLayout()), "West");
		var toglbl = new JLabel("") {
			String text = "◀";

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				var g2 = (Graphics2D) g;
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new JPanel().getBackground());
				g2.fillRoundRect(-10, 0, 30, 30, 5, 5);
				g2.setColor(blue);
				g2.drawString(text, 5, 20);
			}
		};

		toglbl.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (toglbl.text.equals("◀")) {
					sz(w, 0, 0);
					toglbl.text = "▶";
				} else {
					sz(w, 280, 0);
					toglbl.text = "◀";
				}

				revalidate();
				repaint();
			}
		});

		w.add(wn = new JPanel(new BorderLayout(5, 5)), "North");
		w.add(wc = new JPanel(pages = new CardLayout()));
		wn.setBackground(blue);

		var wnn = new JPanel(new BorderLayout(5, 5));
		var wnc = new JPanel(new GridLayout(1, 0, 5, 5));

		wn.add(wnn, "North");
		wn.add(wnc);

		wnn.setOpaque(false);
		wnc.setOpaque(false);

		wnn.add(search = new JTextField(15));
		wnn.add(btn("검색", a -> search()), "East");

		for (var b : "검색,길찾기".split(",")) {
			var btn = btn(b, a -> {
				for (var b2 : wnc.getComponents())
					b2.setBackground(blue);
				pages.show(wc, a.getActionCommand());
				((JButton) a.getSource()).setBackground(blue.darker());
			});
			btn.setBackground(blue);
			if (b.equals("검색"))
				btn.doClick();
			wnc.add(sz(btn, 0, 60));
		}

		wc.add(new JScrollPane(p1 = new JPanel(new BorderLayout())), "검색");
		wc.add(new JScrollPane(p2 = new JPanel(new BorderLayout())), "길찾기");
		p2.add(p2c = new JPanel(new GridLayout(0, 1, 5, 5)));
		var p2n = new JPanel(new BorderLayout(5, 5));
		var p2nc = new JPanel(new GridLayout(0, 1, 5, 5));
		var p2ns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

		p2.add(p2n, "North");
		p2n.add(p2nc);
		p2n.add(p2ns, "South");

		p2nc.add(start = new JTextField());
		p2nc.add(end = new JTextField());

		start.setRequestFocusEnabled(false);
		end.setRequestFocusEnabled(false);

		p2n.add(btn("↑↓", a -> {
			var temp = start.getName();
			start.setName(end.getName());
			end.setName(temp);
			var temp2 = start.getText();
			start.setText(end.getText());
			end.setText(temp2);
			dijkstra();
		}), "East");

		p2.add(p2c = new JPanel(new GridLayout(0, 1)));

		p2ns.add(btn("집을 출발지로", a -> {
			var building = getRows("select b.* from user u inner join building b on u.building = b.no where u.no = ?",
					uno).get(0);
			setPath(building, 0);
			dijkstra();
		}));

		p2n.setBorder(new EmptyBorder(5, 5, 5, 5));
		wnc.setBorder(new EmptyBorder(0, 5, 5, 5));
		wnn.setBorder(new EmptyBorder(5, 5, 0, 5));
		wn.setBorder(new EmptyBorder(5, 5, 5, 5));
		w.add(hyplbl("메인으로", JLabel.LEFT, 13, (e) -> mf.swapPage(new MainPage())), "South");
		cw.setOpaque(false);
		cw.add(sz(toglbl, 20, 30));

	}

	void search() {
		p1.removeAll();
		if (search.getText().trim().isEmpty())
			emsg("공백이 존제합니다.");
		else {
			var rs = getRows(
					"select b.*, ifnull((select round(avg(r.rate),1) from rate r where r.building = b.no)  , 0) from building b where type <> 3 and b.name like '%"
							+ search.getText() + "%' or b.info like '%" + search.getText() + "%';");
			if (rs.isEmpty()) {
				imsg("검색 결과가 없습니다.");
			} else {
				p1.setLayout(new BorderLayout());
				p1.add(lbl("<html>장소명 <Font color = rgb(0,123,255)>" + search.getText() + "</font> 의 검색 결과",
						JLabel.LEFT, 13), "North");

				var p = new JPanel(new GridLayout(0, 1));

				p1.add(p);

				center(cint(rs.get(0).get(6)), cint(rs.get(0).get(7)));

				for (var r : rs) {
					var temp1 = new JPanel(new BorderLayout());
					var temp2 = new JPanel(new BorderLayout());

					temp1.add(hyplbl("<html><font color = 'black'>" + (rs.indexOf(r) + 1) + ". " + r.get(1).toString(),
							JLabel.LEFT, 15, (e) -> new InfoDialog(r).setVisible(true)), "North");
					temp1.addMouseListener(new MouseAdapter() {
						public void mousePressed(MouseEvent e) {
							if (e.getButton() == 1) {
								System.out.println("ㅇㅇ");
								if (e.getClickCount() == 2) {
									center(cint(r.get(6)), cint(r.get(7)));
								}
							}

							if (e.getButton() == 3) {
								selected = r;
								menu.show(temp1, e.getX(), e.getY());
							}
						};
					});

					temp1.add(temp2);

					temp2.add(new JLabel(toIcon(r.get(8), 80, 80)), "East");

					temp2.add(lbl(r.get(4) + "", JLabel.LEFT));
					temp1.add(lbl("평점: " + r.get(9), JLabel.LEFT), "South");
					p.add(temp1);
					sz(temp1, 180, 120);
					temp1.setBorder(new CompoundBorder(new MatteBorder(1, 0, 1, 0, Color.LIGHT_GRAY),
							new EmptyBorder(5, 5, 5, 5)));
				}

				while (p.getComponentCount() < 3)
					p.add(new JLabel());

				p.setBorder(new EmptyBorder(5, 5, 5, 5));
			}
		}

		revalidate();
		repaint();
	}

	void center(int x, int y) {
		try {
			zoom = 1;
			aff.setToIdentity();
			aff.scale(zoom, zoom);
			from = new Point(0, 0);
			to = new Point(c.getWidth() / 2 - x, c.getHeight() / 2 - y);
			sAffPoint = aff.inverseTransform(from, null);
			eAffPoint = aff.inverseTransform(to, null);
			var difx = eAffPoint.getX() - sAffPoint.getX();
			var dify = eAffPoint.getY() - sAffPoint.getY();
			aff.translate(difx, dify);
			c.repaint();
		} catch (NoninvertibleTransformException e) {
			e.printStackTrace();
		}

	}

	private void mapInit() {
		try {
			map = ImageIO.read(new File("./datafiles/map.jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		var g2 = (Graphics2D) map.getGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		if (path != null) {
			for (int i = 0; i < path.size() - 1; i++) {
				int n1 = path.get(i);
				int n2 = path.get(i + 1);
				g2.setColor(Color.YELLOW);
				var pos1 = getRow("select x, y from building where no = ?", n1);
				var pos2 = getRow("select x, y from building where no = ?", n2);
				if (current != null && current.isValidValue(i))
					g2.setColor(Color.magenta);
				g2.drawLine(cint(pos1.get(0)), cint(pos1.get(1)), cint(pos2.get(0)), cint(pos2.get(1)));
			}
		}

		var d = "진료소,병원,주거지".split(",");

		for (var r : objList) {
			var building = (ArrayList<Object>) r[0];
			g2.setColor(Color.red);
			int x = cint(r[1]), y = cint(r[2]);
			BufferedImage img;
			try {
				img = ImageIO.read(new File("./datafiles/맵아이콘/" + d[cint(building.get(5))] + ".png"));
				g2.drawString(building.get(1).toString(),
						(x + 20) - g2.getFontMetrics().stringWidth(building.get(1).toString()) / 2, y - 5);
				g2.drawImage(img, x, y, 40, 40, null);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (c != null)
			c.repaint();
	}

	private void dataInit() {
		objList = new ArrayList<Object[]>();

		for (var r : getRows(
				"select c.node1, c.node2, c.name ,b1.x, b1.y, b2.x, b2.y from connection c, building b1, building b2 where c.node1 = b1.no and c.node2 = b2.no")) {
			int u = cint(r.get(0)), v = cint(r.get(1)), x1 = cint(r.get(3)), y1 = cint(r.get(4)), x2 = cint(r.get(5)),
					y2 = cint(r.get(6));
			int cost = (int) Point2D.distance(x1, y1, x2, y2);

			adj[u][v][0] = adj[v][u][0] = cost;
			adj[u][v][1] = adj[v][u][1] = r.get(2).toString();

		}

		for (var r : getRows(
				"select b.*, ifnull((select round(avg(r.rate),1) from rate r where r.building = b.no)  , 0) from building b where type <>3;")) {
			objList.add(new Object[] { r, cint(r.get(6)) - 20, cint(r.get(7)) - 20 });
		}
	}

	void dijkstra() {

		if (start.getText().isEmpty() || end.getText().isEmpty())
			return;
		int start = cint(this.start.getName());
		int end = cint(this.end.getName());

		int[][] dist = new int[2][346];

		for (int i = 0; i < 346; i++) {
			dist[0][i] = INF;
			dist[1][i] = -1;
		}

		path = new ArrayList<Integer>();

		var pq = new PriorityQueue<Object[]>((o1, o2) -> Integer.compare(cint(o1[1]), cint(o2[1])));
		pq.offer(new Object[] { start, 0 });
		dist[0][start] = 0;

		while (!pq.isEmpty()) {
			var cur = pq.poll();
			int u = cint(cur[0]);
			int cost = cint(cur[1]);
			if (dist[0][u] < cost)
				continue;
			for (int v = 1; v < 346; v++) {
				//팁 단선여부 확인을 꼭 할것 (안그럼 무한 루프)
				if(cint(adj[u][v][0]) == INF) continue;
				var next = adj[u][v];
				int next_cost = cint(next[0]);
				if (dist[0][v] > cost + next_cost) {
					dist[0][v] = cost + next_cost;
					dist[1][v] = u;
					pq.offer(new Object[] { v, dist[0][v] });
				}
			}
		}

		p2c.removeAll();

		int arv = start, dest = end;
		for(int i = 1; i<346; i++) {
			System.out.println(dist[1][i]);
		}
		while (dest != arv) {
			path.add(dest);
			dest = dist[1][dest];
		}

		path.add(arv);

		Collections.reverse(path);

		Stack<java.util.Map.Entry<String, Integer>> s = new Stack();

		// 범위가 필요함;
		ArrayList<ValueRange> validate = new ArrayList<>();
		for (int i = 1; i < path.size(); i++) {
			int n1 = path.get(i - 1);
			int n2 = path.get(i);
			var node = adj[n1][n2];
			if (s.empty() || !s.peek().getKey().equals(node[1] + "")) {
				s.add(java.util.Map.entry(node[1] + "", cint(node[0])));
				validate.add(ValueRange.of(i - 1, i - 1));
			} else {
				var value = s.peek().getValue() + cint(node[0]);
				s.pop();
				s.add(java.util.Map.entry(node[1] + "", value));
				validate.set(validate.size() - 1, ValueRange.of(validate.get(validate.size() - 1).getMinimum(), i));
			}
		}

		int total = s.stream().mapToInt(a -> a.getValue()).sum();
		p2c.add(lbl("총 거리:" + total + "m", JLabel.RIGHT));

		var lst = s.stream().collect(Collectors.toList());
		Collections.reverse(lst);

		for (int i = 0; i < lst.size(); i++) {
			String text = "<html><font color ='black'>";
			if (i == 0)
				text = "<html><font color = 'red'>출발 </font>" + text;
			else if (i == lst.size() - 1)
				text = "<html><font color = 'blue'>도착 </font>" + text;
			final int j = i;
			var hyplbl = hyplbl(text + (i + 1) + ". " + lst.get(i).getKey() + " 총 " + lst.get(i).getValue() + "m",
					JLabel.CENTER, 13, e -> {
						current = validate.get(j);
						mapInit();
					});
			hyplbl.setBorder(new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
			p2c.add(hyplbl);
		}
		mapInit();
		revalidate();
		repaint();

	}

	void setPath(ArrayList<Object> b, int r) {
		var arr = Arrays.asList(start, end);
		int comp = r == 0 ? 1 : 0;
		if (arr.get(comp).getName() != null && arr.get(comp).getName().equals(b.get(0).toString())) {
			emsg("출발지와 도착지는 같을 수 없습니다.");
			return;
		}
		arr.get(r).setName(b.get(0).toString());
		arr.get(r).setText(b.get(1).toString());
	};

	public static void main(String[] args) {
		uno = "1";
		mf.swapPage(new Map());
	}
}
