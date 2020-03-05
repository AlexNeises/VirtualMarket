package es.neis.minecraft.virtualmarket;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;

import es.neis.minecraft.virtualmarket.TableGenerator.Alignment;
import es.neis.minecraft.virtualmarket.TableGenerator.Receiver;

public class VirtualMarket extends JavaPlugin {
	private Connection connection;
	private String host, database, username, password;
	private int port;
	private Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");
	private String sellHelp = ChatColor.GOLD + "/vm sell " + ChatColor.AQUA + "<num_items> <hand | item_name> <price_per_item>" + ChatColor.WHITE + " - Sells the given number of items for the set price per item";
	private String browseHelp = ChatColor.GOLD + "/vm browse " + ChatColor.AQUA + "<hand | item_name>" + ChatColor.WHITE + " - Browse the market for the given item";
	private String buyHelp = ChatColor.GOLD + "/vm buy " + ChatColor.AQUA + "<num_items> <hand | item_name> <total_cost_to_spend>" + ChatColor.WHITE + " - Attempts to buy the given number of items spending no more than the total cost given";
	
	private int getItemDamage(ItemStack itemStack) {
		double maxDurability = itemStack.getType().getMaxDurability();
		double currentDurability = itemStack.getData().getData();
		return (int) ((currentDurability / maxDurability) * 100.0);
	}
	
	public boolean isNumeric(String str) {
	    if (str == null) {
	        return false; 
	    }
	    return pattern.matcher(str).matches();
	}
	
	private void processSell(Player player, String[] args) {
		if (!isNumeric(args[args.length - 1]) || !isNumeric(args[1])) {
			player.sendMessage(sellHelp);
		} else {
			ArrayList<String> ar = new ArrayList<String>();
			for (int i = 2; i < args.length - 1; i++) {
				if (!isNumeric(args[i])) {
					ar.add(args[i]);
				}
			}
			String sellingItem = String.join("_", ar).toUpperCase();
			try {
				XMaterial itemToSell;
				if (sellingItem.equals("HAND")) {
					ItemStack itemStack = new ItemStack(player.getInventory().getItemInMainHand());
					itemToSell = XMaterial.matchXMaterial(itemStack);
				} else {
					itemToSell = XMaterial.matchXMaterial(sellingItem).get();
				}
				checkInventory(player, itemToSell, Integer.parseInt(args[1]), Float.parseFloat(args[args.length - 1]));
			} catch (Exception e) {
				player.sendMessage("Unable to sell the current item.");
			}
		}
	}
	
	private void processBrowse(Player player, String[] args) {
		ArrayList<String> ar = new ArrayList<String>();
		for (int i = 1; i < args.length; i++) {
			if (!isNumeric(args[i])) {
				ar.add(args[i]);
			}
		}
		String browsedItem = String.join("_", ar).toUpperCase();
		try {
			XMaterial itemToFind;
			if (browsedItem.equals("HAND")) {
				ItemStack itemStack = new ItemStack(player.getInventory().getItemInMainHand());
				itemToFind = XMaterial.matchXMaterial(itemStack);
			} else {
				itemToFind = XMaterial.matchXMaterial(browsedItem).get();
			}
			queryMarket(player, itemToFind);
		} catch (Exception e) {
			player.sendMessage("Unable to find the current item.");
		}
	}
	
	private void checkInventory(Player player, XMaterial item, Integer amount, Float price) {
		PlayerInventory inv = player.getInventory();
		int numberSold = 0;
        if (amount <= 0) return;
        int size = inv.getSize();
        boolean exists = false;
        for (int slot = 0; slot < size; slot++) {
            ItemStack is = inv.getItem(slot);
            if (is == null) continue;
            if (item.parseMaterial() == is.getType()) {
            	if (!is.hasItemMeta()) {
	            	if ((XMaterial.matchXMaterial(is).isDamageable() && getItemDamage(is) <= 0) || (!XMaterial.matchXMaterial(is).isDamageable())) {
	            		exists = true;
		                int newAmount = is.getAmount() - amount;
		                if (newAmount > 0) {
		                    is.setAmount(newAmount);
		                    numberSold += amount;
		                    break;
		                } else {
		                    inv.clear(slot);
		                    numberSold += is.getAmount();
		                    amount = -newAmount;
		                    if (amount == 0) break;
		                }
	            	} else {
	            		player.sendMessage("Unable to sell damaged items at this time.");
	            		return;
	            	}
            	} else {
                	player.sendMessage("Unable to sell enchanted items at this time.");
                	return;
                }
            }
        }
        if (exists) {
        	listOnMarket(player, item, numberSold, price);
        	player.sendMessage("You sold " + Integer.toString(numberSold) + " " + item.toString() + " for $" + Float.toString(price) + " per item.");
        } else {
        	player.sendMessage("This item doesn't exist in your inventory.");
        }
	}
	
	private void queryMarket(Player player, XMaterial item) {
		try {
			openConnection();
			Statement statement = connection.createStatement();
			try {
				ResultSet rs = statement.executeQuery("SELECT * FROM " + this.database + ".market WHERE item_name LIKE '" + item.toString() + "' ORDER BY id ASC, item_price DESC");
				int count = 0;
				while (rs.next())
				{
					if (count == 9) {
						player.sendMessage("And more...");
						break;
					}
					DecimalFormat df = new DecimalFormat("0.00");
					player.sendMessage(ChatColor.RED + rs.getString("user_name") + ChatColor.WHITE + ": " + ChatColor.GOLD + rs.getString("item_amount") + " " + ChatColor.BLUE + rs.getString("item_name") + ChatColor.WHITE + " for " + ChatColor.YELLOW + "$" + df.format(rs.getFloat("item_price")) + ChatColor.WHITE + " each.");
					count++;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private void listOnMarket(Player player, XMaterial item, Integer numberSold, Float pricePerItem) {
		try {
			openConnection();
			Statement statement = connection.createStatement();
			try {
				statement.executeUpdate("INSERT INTO " + this.database + ".market ("
						+ "`item_name`, "
						+ "`item_price`, "
						+ "`item_amount`, "
						+ "`user_name`, "
						+ "`user_uuid`) VALUES ('" + item.toString() + "', "
						+ "" + pricePerItem + ", "
						+ "" + numberSold + ", "
						+ "'" + player.getName() + "', "
						+ "'" + player.getUniqueId() + "')");
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private boolean processCommand(Player player, String[] args) {
		if (args.length > 0) {
			switch (args[0].toLowerCase()) {
				case "help":
				case "?":
					sendHelpMenu(player);
					break;
				case "buy":
					if (handleHelpCases(player, args, buyHelp)) {
						//Do code to buy
					}
					break;
				case "sell":
					if (handleHelpCases(player, args, sellHelp)) {
						processSell(player, args);
					}
					break;
				case "browse":
					if (handleHelpCases(player, args, browseHelp)) {
						processBrowse(player, args);
					}
					break;
				case "info":
					break;
				default:
					//This isn't a valid command
					player.sendMessage(ChatColor.GOLD + "/vm " + args[0] + ChatColor.WHITE + " is not a valid command, please use /vm ? to view all commands");
			}
		} else {
			sendHelpMenu(player);
		}
		return true;
	}
	
	/**
	 * Tests if this command has a ? or help. For example "/vm buy ?". If so, it'll display the help message and return false.
	 * @param player Player that will receive the help message
	 * @param args arguments to test against
	 * @param helpCaseToDisplay The help message to display
	 * @return False if a help message was displayed, true if this is a normal command
	 */
	public boolean handleHelpCases(Player player, String[] args, String helpCaseToDisplay) {
		if (args.length < 1) {
			switch (args[1].toLowerCase()) {
			case "help":
			case "?":
				player.sendMessage(helpCaseToDisplay); //This is triggered with commands like "/vm buy ?" or "/vm buy help"
				return false;
			default:
				return true;
			}
		} else {
			player.sendMessage(helpCaseToDisplay); //there are not enough arguments. EX: "/vm buy"
			return false; 
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("vm") || cmd.getName().equalsIgnoreCase("virtualmarket")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;
				processCommand(player, args);
			}
			return true;
		}
		return false; 
	}
	
	@Override
	public void onEnable() {
		getLogger().info("Enabling VirtualMarket!");
		
		host = this.getConfig().getString("database.host", "localhost");
		database = this.getConfig().getString("database.database", "neises_virtualmarket");
		username = this.getConfig().getString("database.username", "root");
		password = this.getConfig().getString("database.password", "password");
		port = this.getConfig().getInt("database.port", 3306);
		try {
			openConnection();
			Statement statement = connection.createStatement();
			try {
				createSchema(statement);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void createSchema(Statement statement) throws SQLException {
		statement.executeUpdate("CREATE DATABASE IF NOT EXISTS " + this.database);
		String sqlStatement = "CREATE TABLE IF NOT EXISTS `" + this.database + "`.`market` ( `id` INT NOT NULL AUTO_INCREMENT, `item_name` VARCHAR(256) NULL, `item_price` DECIMAL(10,2) NULL, `item_amount` INT(11) NULL, `user_name` VARCHAR(256) NULL, `user_uuid` VARCHAR(256) NULL, PRIMARY KEY (`id`), UNIQUE INDEX `id_UNIQUE` (`id` ASC) VISIBLE) ENGINE = InnoDB";
		getLogger().info(sqlStatement);
		statement.executeUpdate(sqlStatement);
	}
	
	@Override
	public void onDisable() {
		getLogger().info("onDisable has been invoked!");
	}
	
	public void openConnection() throws SQLException, ClassNotFoundException {
		if (connection != null && !connection.isClosed()) {
			return;
		}
		
		synchronized (this) {
			if (connection != null && !connection.isClosed()) {
				return;
			}
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection("jdbc:mysql://" + this.host + ":" + this.port + "?useSSL=false", this.username, this.password);
		}
	}
	
	public void sendHelpMenu(Player player) {
		player.sendMessage(ChatColor.GOLD + "/vm" + ChatColor.WHITE + " - Displays this menu");
		player.sendMessage(ChatColor.GOLD + "/vm buy ? " + ChatColor.WHITE + " - Display help for buying");
		player.sendMessage(ChatColor.GOLD + "/vm sell ? " + ChatColor.WHITE + " - Display help for selling");
		player.sendMessage(ChatColor.GOLD + "/vm browse ? " + ChatColor.WHITE + " - Display help for browsing");

	}
}
